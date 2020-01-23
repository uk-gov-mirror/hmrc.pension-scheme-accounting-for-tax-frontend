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

package pages.chargeC

import models.UserAnswers
import pages.QuestionPage
import play.api.libs.json.JsPath

import scala.util.Try

case class IsSponsoringEmployerIndividualPage(index: Int) extends QuestionPage[Boolean] {

  override def path: JsPath = SponsoringEmployersQuery(index).path \ IsSponsoringEmployerIndividualPage.toString

  override def cleanup(value: Option[Boolean], userAnswers: UserAnswers): Try[UserAnswers] = {
    val tidyResult = value match {
      case Some(true) if userAnswers.get(SponsoringOrganisationDetailsPage(index)).isDefined =>
        userAnswers
          .remove(SponsoringOrganisationDetailsPage(index)).toOption.getOrElse(userAnswers)
          .remove(SponsoringEmployerAddressPage(index)).toOption.getOrElse(userAnswers)
          .remove(ChargeCDetailsPage(index)).toOption
      case Some(false) if userAnswers.get(SponsoringIndividualDetailsPage(index)).isDefined =>
        userAnswers
          .remove(SponsoringIndividualDetailsPage(index)).toOption.getOrElse(userAnswers)
          .remove(SponsoringEmployerAddressPage(index)).toOption.getOrElse(userAnswers)
          .remove(ChargeCDetailsPage(index)).toOption
      case _ => None
    }
    super.cleanup(value, tidyResult.getOrElse(userAnswers))
  }
}

object IsSponsoringEmployerIndividualPage {
  override lazy val toString: String = "isSponsoringEmployerIndividual"
}
