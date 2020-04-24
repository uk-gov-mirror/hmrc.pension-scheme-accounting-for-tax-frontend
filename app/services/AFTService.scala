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

package services

import java.time.{LocalDateTime, LocalDate, LocalTime}

import com.google.inject.Inject
import connectors.cache.UserAnswersCacheConnector
import connectors.{AFTConnector, MinimalPsaConnector}
import javax.inject.Singleton
import models.AFTOverview
import models.AccessMode
import models.LocalDateBinder._
import models.SchemeStatus.statusByName
import models.SessionData
import models.SessionDataMinusLockInfo
import models.requests.{DataRequest, OptionalDataRequest}
import models.{SchemeDetails, StartQuarters, UserAnswers}
import pages._
import play.api.libs.json._
import uk.gov.hmrc.http.HeaderCarrier
import utils.DateHelper

import scala.concurrent.{Future, ExecutionContext}
import scala.util.{Success, Failure}

@Singleton
class AFTService @Inject()(
    aftConnector: AFTConnector,
    userAnswersCacheConnector: UserAnswersCacheConnector,
    schemeService: SchemeService,
    minimalPsaConnector: MinimalPsaConnector,
    aftReturnTidyService: AFTReturnTidyService
) {

  def fileAFTReturn(pstr: String, answers: UserAnswers)(implicit ec: ExecutionContext, hc: HeaderCarrier, request: DataRequest[_]): Future[Unit] = {

    val hasDeletedLastMemberOrEmployerFromLastCharge = !aftReturnTidyService.isAtLeastOneValidCharge(answers)

    val ua = if (hasDeletedLastMemberOrEmployerFromLastCharge) {
      aftReturnTidyService.reinstateDeletedMemberOrEmployer(answers)
    } else {
      aftReturnTidyService.removeChargesHavingNoMembersOrEmployers(answers)
    }

    aftConnector.fileAFTReturn(pstr, ua).flatMap { _ =>
      if (hasDeletedLastMemberOrEmployerFromLastCharge) {
        userAnswersCacheConnector.removeAll(request.internalId).map(_ => ())
      } else {
        ua.remove(IsNewReturn) match {
          case Success(userAnswersWithIsNewReturnRemoved) =>
            userAnswersCacheConnector.save(request.internalId, userAnswersWithIsNewReturnRemoved.data).map(_ => ())
          case Failure(ex) => throw ex
        }
      }
    }
  }

  def getAFTDetails(pstr: String, startDate: String, aftVersion: String)(implicit ec: ExecutionContext, hc: HeaderCarrier): Future[JsValue] =
    aftConnector.getAFTDetails(pstr, startDate, aftVersion)

  def retrieveAFTRequiredDetails(srn: String, startDate: LocalDate, optionVersion: Option[String])(
      implicit hc: HeaderCarrier,
      ec: ExecutionContext,
      request: OptionalDataRequest[_]): Future[(SchemeDetails, UserAnswers)] = {
    for {
      schemeDetails <- schemeService.retrieveSchemeDetails(request.psaId.id, srn)
      updatedUA <- updateUserAnswersWithAFTDetails(optionVersion, schemeDetails, startDate)
      savedUA <- save(updatedUA, srn, startDate, optionVersion, schemeDetails.pstr)
    } yield {
      (schemeDetails, savedUA)
    }
  }

  private def createSessionData(optionVersion: Option[String], seqAFTOverview: Seq[AFTOverview], isLocked: Boolean, psaSuspended: Boolean) = {
    val maxVersion = seqAFTOverview.headOption.map(_.numberOfVersions).getOrElse(1)

    val version = optionVersion match {
      case None => maxVersion
      case Some(v) => v.toInt
    }

    val accessMode = if (isLocked || psaSuspended || version < maxVersion) {
      AccessMode.PageAccessModeViewOnly
    } else {
      if (seqAFTOverview.isEmpty) {
        AccessMode.PageAccessModePreCompile
      } else {
        if (seqAFTOverview.head.compiledVersionAvailable) {
          AccessMode.PageAccessModeCompile
        } else {
          AccessMode.PageAccessModePreCompile
        }
      }
    }
    SessionDataMinusLockInfo(version, accessMode)
  }

  private def saveByAccessMode(id:String, ua: UserAnswers, sd:SessionDataMinusLockInfo)(implicit request: OptionalDataRequest[_], hc: HeaderCarrier, ec: ExecutionContext) = {
    if (sd.accessMode == AccessMode.PageAccessModeViewOnly) {
      userAnswersCacheConnector
        .save(
          id,
          ua.data
        )
    } else {
      userAnswersCacheConnector
        .saveAndLock(
          id,
          ua.data,
          Some(sd)
        )
    }
  }

  private def save(ua: UserAnswers,
                   srn:String,
                   startDate: LocalDate,
                   optionVersion: Option[String],
                   pstr:String)(implicit request: OptionalDataRequest[_], hc: HeaderCarrier, ec: ExecutionContext): Future[UserAnswers] = {
    val psaSuspended = ua.get(IsPsaSuspendedQuery).getOrElse(true)
    val id = s"$srn$startDate"

    for {
      optionSessionData <- userAnswersCacheConnector.getSessionData(id)
      seqAFTOverview <- aftConnector.getAftOverview(pstr)
      savedJson <- saveByAccessMode(
        request.internalId,
        ua,
        createSessionData(optionVersion, seqAFTOverview, optionSessionData.exists(_.isLocked), psaSuspended)
      )
    } yield {
      UserAnswers(savedJson.as[JsObject])
    }
  }

  private def updateUserAnswersWithAFTDetails(optionVersion: Option[String], schemeDetails: SchemeDetails, startDate: LocalDate)(
      implicit hc: HeaderCarrier,
      ec: ExecutionContext,
      request: OptionalDataRequest[_]): Future[UserAnswers] = {
    def currentUserAnswers: UserAnswers = request.userAnswers.getOrElse(UserAnswers())

    val futureUserAnswers = optionVersion match {
      case None =>
        aftConnector.getListOfVersions(schemeDetails.pstr, startDate).map { listOfVersions =>
          if (listOfVersions.isEmpty) {
            currentUserAnswers
              .setOrException(IsNewReturn, true)
              .setOrException(QuarterPage, StartQuarters.getQuarter(startDate))
              .setOrException(AFTStatusQuery, value = "Compiled")
              .setOrException(SchemeNameQuery, schemeDetails.schemeName)
              .setOrException(PSTRQuery, schemeDetails.pstr)
          } else {
            currentUserAnswers
          }
        }
      case Some(version) =>
        getAFTDetails(schemeDetails.pstr, startDate, version)
          .map(aftDetails => UserAnswers(aftDetails.as[JsObject]))
    }

    futureUserAnswers.flatMap { ua =>
      val uaWithStatus = ua.setOrException(SchemeStatusQuery, statusByName(schemeDetails.schemeStatus))
      uaWithStatus.get(IsPsaSuspendedQuery) match {
        case None =>
          minimalPsaConnector.getMinimalPsaDetails(request.psaId.id).map { psaDetails =>
            uaWithStatus.setOrException(IsPsaSuspendedQuery, psaDetails.isPsaSuspended)
              .setOrException(PSAEmailQuery, psaDetails.email)
          }
        case Some(_) =>
          Future.successful(uaWithStatus)
      }
    }
  }

  def isSubmissionDisabled(quarterEndDate: String): Boolean = {
    val nextDay = LocalDate.parse(quarterEndDate).plusDays(1)
    !(DateHelper.today.compareTo(nextDay) >= 0)
  }
}
