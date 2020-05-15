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

import base.SpecBase
import data.SampleData.sessionId
import models.requests.DataRequest
import models.{AccessMode, CheckMode, NormalMode, SessionAccessData, SessionData, UserAnswers}
import org.mockito.Mockito.when
import org.scalatest.concurrent.ScalaFutures
import org.scalatestplus.mockito.MockitoSugar
import pages.QuestionPage
import play.api.libs.json.{JsPath, Json}
import play.api.mvc.AnyContent
import play.api.test.FakeRequest
import play.api.test.Helpers.GET
import uk.gov.hmrc.domain.PsaId
import utils.DeleteChargeHelper

import scala.concurrent.Future

class UserAnswersServiceSpec extends SpecBase with MockitoSugar with ScalaFutures {

  import UserAnswersServiceSpec._
  val mockDeleteChargeHelper: DeleteChargeHelper = mock[DeleteChargeHelper]
  val service: UserAnswersService = new UserAnswersService(mockDeleteChargeHelper)

  ".remove" must {
    "FIRST COMPILE - set only the page value for a scheme level charge being deleted if version is 1" in {

      val result = service.remove(Page)(dataRequest(ua))

       result mustBe ua
    }

    "AMENDMENT - set amended version to null and the page value for a scheme level charge being deleted if version is 2" in {
      when(mockDeleteChargeHelper.zeroOutCharge(Page, uaVersion2)).thenReturn(uaVersion2)
      val result = service.remove(Page)(dataRequest(uaVersion2, 2))

       result mustBe UserAnswers(Json.obj(
        Page.toString -> Json.obj("value" -> pageValue)
      ))
    }
  }

  ".set" must {

    "AMENDMENT - set amended version to null and the page value for a scheme level charge being added if version is 2" in {

      val resultFuture = Future.fromTry(service.set(Page, pageValue, NormalMode, isMemberBased = false)(dataRequest(version =  2), implicitly))

      whenReady(resultFuture){ _ mustBe UserAnswers(Json.obj(
        Page.toString -> pageValue
      ))}
    }

    "FIRST COMPILE - set only the page value for a scheme level charge being added if version is 1" in {
      val resultFuture = Future.fromTry(service.set(Page, pageValue, NormalMode, isMemberBased = false)(dataRequest(), implicitly))

      whenReady(resultFuture){ _ mustBe UserAnswers(Json.obj(
        Page.toString -> pageValue
      ))}
    }

    "FIRST COMPILE - set only the page value for a member level charge being added if version is 1" in {
      val resultFuture = Future.fromTry(service.set(MemberPage, pageValue, NormalMode)(dataRequest(), implicitly))

      whenReady(resultFuture){ _ mustBe UserAnswers(Json.obj(
        "chargeType" -> Json.obj("members" -> Json.arr(Json.obj(MemberPage.toString -> "value")))
      ))}
    }

    "FIRST COMPILE - set only the page value for a member level charge being changed if version is 1" in {
      val resultFuture = Future.fromTry(service.set(MemberPage, pageValue, CheckMode)(dataRequest(), implicitly))

      whenReady(resultFuture){ _ mustBe UserAnswers(Json.obj(
        "chargeType" -> Json.obj("members" -> Json.arr(Json.obj(MemberPage.toString -> "value")))
      ))}
    }

    "FIRST COMPILE - set only the page value for a member level charge being deleted if version is 1" in {
      val resultFuture = Future.fromTry(service.removeMemberBasedCharge(MemberPage, pageValue, total)(dataRequest(ua), implicitly))

      whenReady(resultFuture){ _ mustBe ua}
    }

    "AMENDMENT - set amended version, member version to null, status to New and the page value" +
      " for a scheme level charge if version is 2 for a new member being added" in {
      val resultFuture = Future.fromTry(service.set(MemberPage, pageValue, NormalMode)(dataRequest(version = 2), implicitly))

      whenReady(resultFuture){ _ mustBe UserAnswers(Json.obj(
        "chargeType" -> Json.obj(
          "members" -> Json.arr(
                    Json.obj(
                        MemberPage.toString -> "value",
                        "memberStatus" -> "New"
                    )
          )
      )))}
    }

    "AMENDMENT - set amended version, member version to null, status to Changed and the page value" +
      " for a scheme level charge if version is 2 for a member being changed after the last submission" in {
      val resultFuture = Future.fromTry(service.set(MemberPage, pageValue, CheckMode)(dataRequest(memberUa(), 2), implicitly))

      whenReady(resultFuture){ _ mustBe UserAnswers(Json.obj(
        "chargeType" -> Json.obj(
          "members" -> Json.arr(
            Json.obj(
              MemberPage.toString -> "value",
              "memberStatus" -> "Changed"
            )
          ))
      ))}
    }

    "AMENDMENT - set amended version, member version to null, status to New and the page value" +
      " for a scheme level charge if version is 2 if a member that was added after the last submission is being changed" in {
      val resultFuture = Future.fromTry(service.set(MemberPage, pageValue, CheckMode)(dataRequest(memberUa(2), 2), implicitly))

      whenReady(resultFuture){ _ mustBe UserAnswers(Json.obj(
        "chargeType" -> Json.obj(
          "members" -> Json.arr(
            Json.obj(
              MemberPage.toString -> "value",
              "memberStatus" -> "New"
            )
          ))
      ))}
    }

    "AMENDMENT - set amended version, member version to null, status to Deleted and the page value" +
      " for a scheme level charge if version is 2 for a member being deleted" in {
      val resultFuture = Future.fromTry(service.removeMemberBasedCharge(MemberPage, pageValue, total)(dataRequest(memberUa(), version = 2), implicitly))

      whenReady(resultFuture){ _ mustBe UserAnswers(Json.obj(
        "chargeType" -> Json.obj(
          "members" -> Json.arr(
            Json.obj(
              MemberPage.toString -> "value",
              "memberStatus" -> "Deleted"
            )
          ))
      ))}
    }

    "AMENDMENT - set amended version, member version to null, status to New and the page value" +
      " for a scheme level charge if version is 2 if a member that was added after the last submission is being deleted" in {
      val resultFuture = Future.fromTry(service.removeMemberBasedCharge(MemberPage, pageValue, total)(dataRequest(memberUa(2), version = 2), implicitly))

      whenReady(resultFuture){ _ mustBe UserAnswers(Json.obj(
        "chargeType" -> Json.obj(
          "members" -> Json.arr(
            Json.obj(
              MemberPage.toString -> "value",
              "memberStatus" -> "New"
            )
          ))
      ))}
    }
  }
}

object UserAnswersServiceSpec {

  case object Page extends QuestionPage[String] {
    override def path: JsPath = JsPath \ toString

    override def toString: String = "page"
  }

  case object MemberPage extends QuestionPage[String] {
    override def path: JsPath = JsPath \ "chargeType" \ "members" \ 0 \ toString

    override def toString: String = "memberPage"
  }

  val pageValue: String = "value"
  val total: UserAnswers => BigDecimal = _ => BigDecimal(100.00)
  val totalZero: UserAnswers => BigDecimal = _ => BigDecimal(0.00)

  def sessionData(version: Int): SessionData =
    SessionData(sessionId, None, SessionAccessData(version, AccessMode.PageAccessModeCompile))

  def dataRequest(ua: UserAnswers = UserAnswers(), version: Int = 1): DataRequest[AnyContent] =
    DataRequest(FakeRequest(GET, "/"), "test-internal-id", PsaId("A2100000"), ua, sessionData(version))

  val ua: UserAnswers = UserAnswers(Json.obj(Page.toString -> pageValue))
  val uaVersion2: UserAnswers = UserAnswers(Json.obj(Page.toString -> Json.obj("value" -> pageValue, "amendedVersion" -> 1)))

  def memberUa(version: Int = 1, status: String = "New"): UserAnswers = UserAnswers(Json.obj(
    "chargeType" -> Json.obj(
      "members" -> Json.arr(
        Json.obj(
          MemberPage.toString -> "value",
          "memberAFTVersion"-> version,
          "memberStatus" -> status
        )
      ),
      "amendedVersion" -> version)
  ))
}

