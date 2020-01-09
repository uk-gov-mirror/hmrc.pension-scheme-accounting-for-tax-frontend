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

package forms.chargeG

import java.time.LocalDate

import forms.mappings.Mappings
import javax.inject.Inject
import models.chargeG.ChargeDetails
import play.api.data.Form
import play.api.data.Forms.mapping

class ChargeDetailsFormProvider @Inject() extends Mappings {

  def apply(dateErrorMsg: String): Form[ChargeDetails] =
    Form(mapping(
      "qropsReferenceNumber" -> text(
        errorKey = "chargeG.chargeDetails.qropsReferenceNumber.error.required"
      ).verifying(regexp("""^Q[0-9]{6}""", "chargeG.chargeDetails.qropsReferenceNumber.error.valid")),
      "qropsTransferDate" -> localDate(
        invalidKey     = "chargeG.chargeDetails.qropsTransferDate.error.invalid",
        allRequiredKey = "chargeG.chargeDetails.qropsTransferDate.error.required.all",
        twoRequiredKey = "chargeG.chargeDetails.qropsTransferDate.error.required.two",
        requiredKey    = "chargeG.chargeDetails.qropsTransferDate.error.required"
      ).verifying(
        minDate(LocalDate.of(2020, 4, 1), dateErrorMsg),
        maxDate(LocalDate.of(2020, 6, 30), dateErrorMsg)
      )
    )(ChargeDetails.apply)(ChargeDetails.unapply))
}
