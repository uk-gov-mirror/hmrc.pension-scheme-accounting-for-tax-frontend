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

package navigators

import config.FrontendAppConfig
import controllers.chargeG.routes._
import data.SampleData
import models.LocalDateBinder._
import models.{CheckMode, NormalMode, UserAnswers}
import org.scalatest.prop.TableFor3
import pages.chargeG._
import pages.{Page, chargeA, chargeB}
import play.api.mvc.Call
import utils.AFTConstants.QUARTER_START_DATE

class ChargeGNavigatorSpec extends NavigatorBehaviour {

  import ChargeGNavigatorSpec._
  private def config: FrontendAppConfig = injector.instanceOf[FrontendAppConfig]
  private val navigator: CompoundNavigator = injector.instanceOf[CompoundNavigator]

  "NormalMode" must {
    def normalModeRoutes: TableFor3[Page, UserAnswers, Call] =
      Table(
        ("Id", "UserAnswers", "Next Page"),
        row(WhatYouWillNeedPage)(MemberDetailsController.onPageLoad(NormalMode,srn, startDate, index)),
        row(MemberDetailsPage(index))(ChargeDetailsController.onPageLoad(NormalMode,srn, startDate, index)),
        row(ChargeDetailsPage(index))(ChargeAmountsController.onPageLoad(NormalMode,srn, startDate, index)),
        row(ChargeAmountsPage(index))(CheckYourAnswersController.onPageLoad(srn, startDate, index)),
        row(CheckYourAnswersPage)(AddMembersController.onPageLoad(srn, startDate)),
        row(AddMembersPage)(MemberDetailsController.onPageLoad(NormalMode,srn, startDate, index), addMembersYes),
        row(AddMembersPage)(controllers.routes.AFTSummaryController.onPageLoad(srn, startDate, None), addMembersNo),
        row(DeleteMemberPage)(Call("GET", config.managePensionsSchemeSummaryUrl.format(srn)), onlyOneCharge),
        row(DeleteMemberPage)(controllers.routes.AFTSummaryController.onPageLoad(srn, startDate, None), multipleCharges),
        row(DeleteMemberPage)(AddMembersController.onPageLoad(srn, startDate), Some(SampleData.chargeGMember))
      )

    behave like navigatorWithRoutesForMode(NormalMode)(navigator, normalModeRoutes,srn, startDate)
  }

  "CheckMode" must {
    def checkModeRoutes: TableFor3[Page, UserAnswers, Call] =
      Table(
        ("Id", "UserAnswers", "Next Page"),
        row(MemberDetailsPage(index))(CheckYourAnswersController.onPageLoad(srn, startDate, index)),
        row(ChargeDetailsPage(index))(CheckYourAnswersController.onPageLoad(srn, startDate, index)),
        row(ChargeAmountsPage(index))(CheckYourAnswersController.onPageLoad(srn, startDate, index))
      )

    behave like navigatorWithRoutesForMode(CheckMode)(navigator, checkModeRoutes,srn, startDate)
  }

}

object ChargeGNavigatorSpec {
  private val srn = "test-srn"
  private val startDate = QUARTER_START_DATE
  private val index = 0
  private val addMembersYes = UserAnswers().set(AddMembersPage, true).toOption
  private val addMembersNo = UserAnswers().set(AddMembersPage, false).toOption
  private val onlyOneCharge = UserAnswers().set(chargeA.ChargeDetailsPage, SampleData.chargeAChargeDetails).toOption
  private val multipleCharges = UserAnswers().set(chargeA.ChargeDetailsPage, SampleData.chargeAChargeDetails)
    .flatMap(_.set(chargeB.ChargeBDetailsPage, SampleData.chargeBDetails)).toOption
}
