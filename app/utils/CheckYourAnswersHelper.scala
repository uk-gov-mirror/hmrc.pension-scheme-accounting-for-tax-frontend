/*
 * Copyright 2019 HM Revenue & Customs
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

package utils

import java.time.format.DateTimeFormatter

import controllers.chargeF.routes
import models.{CheckMode, UserAnswers}
import pages._
import play.api.i18n.Messages
import CheckYourAnswersHelper._
import uk.gov.hmrc.viewmodels._
import uk.gov.hmrc.viewmodels.SummaryList._
import uk.gov.hmrc.viewmodels.Text.Literal

class CheckYourAnswersHelper(userAnswers: UserAnswers, srn: String)(implicit messages: Messages) {

  def date: Option[Row] = userAnswers.get(ChargeDetailsPage) map {
    answer =>
      Row(
        key = Key(msg"chargeDetails.date.checkYourAnswersLabel", classes = Seq("govuk-!-width-one-half")),
        value = Value(Literal(answer.deRegistrationDate.format(dateFormatter))),
        actions = List(
          Action(
            content = msg"site.edit",
            href = routes.ChargeDetailsController.onPageLoad(CheckMode, srn).url,
            visuallyHiddenText = Some(msg"site.edit.hidden".withArgs(msg"chargeDetails.checkYourAnswersLabel"))
          )
        )
      )
  }

  def amount: Option[Row] = userAnswers.get(ChargeDetailsPage) map {
    answer =>
      Row(
        key = Key(msg"chargeDetails.amount.checkYourAnswersLabel", classes = Seq("govuk-!-width-one-half")),
        value = Value(Literal(answer.amountTaxDue.toString())),
        actions = List(
          Action(
            content = msg"site.edit",
            href = routes.ChargeDetailsController.onPageLoad(CheckMode, srn).url,
            visuallyHiddenText = Some(msg"site.edit.hidden".withArgs(msg"chargeDetails.checkYourAnswersLabel"))
          )
        )
      )
  }

  private def yesOrNo(answer: Boolean): Content =
    if (answer) {
      msg"site.yes"
    } else {
      msg"site.no"
    }
}

object CheckYourAnswersHelper {

  private val dateFormatter = DateTimeFormatter.ofPattern("d MMMM yyyy")
}
