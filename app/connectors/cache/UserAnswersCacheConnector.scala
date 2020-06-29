/*
 * Copyright 2020 HM Revenue & Customs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package connectors.cache

import com.google.inject.Inject
import config.FrontendAppConfig
import models.SessionData
import models.SessionAccessData
import play.api.http.Status._
import play.api.libs.json.JsError
import play.api.libs.json.JsResultException
import play.api.libs.json.JsSuccess
import play.api.libs.json.JsValue
import play.api.libs.json.Json
import play.api.libs.ws.WSClient
import play.api.mvc.Result
import play.api.mvc.Results._
import uk.gov.hmrc.crypto.PlainText
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.http.HttpException

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

class UserAnswersCacheConnectorImpl @Inject()(
    config: FrontendAppConfig,
    http: WSClient
) extends UserAnswersCacheConnector {

  override protected def saveUrl = s"${config.aftUrl}/pension-scheme-accounting-for-tax/journey-cache/aft"
  override protected def saveSessionUrl = s"${config.aftUrl}/pension-scheme-accounting-for-tax/journey-cache/aft/session-data"
  override protected def saveSessionAndLockUrl = s"${config.aftUrl}/pension-scheme-accounting-for-tax/journey-cache/aft/session-data-lock"
  override protected def lockedByUrl = s"${config.aftUrl}/pension-scheme-accounting-for-tax/journey-cache/aft/lock"

  override def fetch(id: String)(implicit
                                 ec: ExecutionContext,
                                 hc: HeaderCarrier): Future[Option[JsValue]] = {
    http
      .url(saveUrl)
      .withHttpHeaders(hc.withExtraHeaders(("id", id)).headers: _*)
      .get()
      .flatMap { response =>
        response.status match {
          case NOT_FOUND =>
            Future.successful(None)
          case OK =>
            Future.successful(Some(Json.parse(response.body)))
          case _ =>
            Future.failed(new HttpException(response.body, response.status))
        }
      }
  }

  def save(id: String, value: JsValue)
          (implicit ec: ExecutionContext, hc: HeaderCarrier): Future[JsValue] = {

    val allExtraHeaders = Seq(Tuple2("id", id), Tuple2("content-type", "application/json"))

    savePost(allExtraHeaders, saveUrl, value)
  }

  private def savePost(headers: Seq[(String, String)], url: String, value: JsValue)
                      (implicit ec: ExecutionContext, hc: HeaderCarrier): Future[JsValue]= {
    http
      .url(url)
      .withHttpHeaders(hc.withExtraHeaders(headers: _*).headers: _*)
      .post(PlainText(Json.stringify(value)).value)
      .flatMap { response =>
        response.status match {
          case CREATED =>
            Future.successful(value)
          case _ =>
            Future.failed(new HttpException(response.body, response.status))
        }
      }
  }

  override def saveAndLock(
    id: String,
    value: JsValue,
    sessionAccessData: SessionAccessData,
  lockReturn: Boolean = false
  )(implicit
  ec: ExecutionContext,
  hc: HeaderCarrier): Future[JsValue] = {

    val useURL = if(lockReturn) saveSessionAndLockUrl else saveSessionUrl

    val sessionDataHeaders = Seq(
        Tuple2("version", sessionAccessData.version.toString),
        Tuple2("accessMode", sessionAccessData.accessMode.toString),
        Tuple2("areSubmittedVersionsAvailable", sessionAccessData.areSubmittedVersionsAvailable.toString))
    val allExtraHeaders = Seq(Tuple2("id", id), Tuple2("content-type", "application/json")) ++ sessionDataHeaders

    savePost(allExtraHeaders, useURL, value)
  }

  override def removeAll(id: String)(implicit ec: ExecutionContext, hc: HeaderCarrier): Future[Result] = {
    http
      .url(saveUrl)
      .withHttpHeaders(hc.withExtraHeaders(("id", id)).headers: _*)
      .delete()
      .map(_ => Ok)
  }

  override def getSessionData(id: String)(implicit
                                          ec: ExecutionContext,
                                          hc: HeaderCarrier): Future[Option[SessionData]] = {
    http
      .url(saveSessionUrl)
      .withHttpHeaders(hc.withExtraHeaders(("id", id)).headers: _*)
      .get()
      .flatMap { response =>
        response.status match {
          case NOT_FOUND => Future.successful(None)
          case OK =>
            val sessionData = Json.parse(response.body).validate[SessionData] match {
              case JsSuccess(value, path) => value
              case JsError(errors)        => throw JsResultException(errors)
            }
            Future.successful(Some(sessionData))
          case _ => Future.failed(new HttpException(response.body, response.status))
        }
      }
  }

  override def lockedBy(srn: String, startDate: String)(implicit
                                               ec: ExecutionContext,
                                               hc: HeaderCarrier
  ): Future[Option[String]] = {
    http.url(lockedByUrl)
      .withHttpHeaders(hc.withExtraHeaders(("id", srn + startDate)).headers: _*)
      .get()
      .flatMap {
        response =>
          response.status match {
            case NOT_FOUND =>
              Future.successful(None)
            case OK =>
              Future.successful(Some(response.body))
            case _ =>
              Future.failed(new HttpException(response.body, response.status))
          }
      }
  }


}

trait UserAnswersCacheConnector {

  protected def saveUrl: String
  protected def saveSessionUrl: String
  protected def saveSessionAndLockUrl: String
  protected def lockedByUrl: String

  def fetch(cacheId: String)(implicit ec: ExecutionContext, hc: HeaderCarrier): Future[Option[JsValue]]

  def save(cacheId: String, value: JsValue
          )(implicit ec: ExecutionContext, hc: HeaderCarrier): Future[JsValue]

  def removeAll(cacheId: String)(implicit ec: ExecutionContext, hc: HeaderCarrier): Future[Result]

  def getSessionData(id: String)(implicit
                                 ec: ExecutionContext,
                                 hc: HeaderCarrier): Future[Option[SessionData]]

  def lockedBy(srn: String, startDate: String)(implicit
                                               ec: ExecutionContext,
                                               hc: HeaderCarrier
  ): Future[Option[String]]

  def saveAndLock(id: String,
                  value: JsValue,
                  sessionAccessData: SessionAccessData,
                  lockReturn: Boolean = false
                  )(implicit
                    ec: ExecutionContext,
                    hc: HeaderCarrier): Future[JsValue]

}
