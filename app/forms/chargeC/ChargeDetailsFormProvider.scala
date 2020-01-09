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

package forms.chargeC


import forms.mappings.Mappings
import javax.inject.Inject
import models.chargeC.ChargeCDetails
import play.api.data.Form
import play.api.data.Forms.mapping

class ChargeDetailsFormProvider @Inject() extends Mappings {

  def apply(): Form[ChargeCDetails] =
    Form(mapping(
      "paymentDate" -> localDate(
        invalidKey = "chargeC.paymentDate.error.invalid",
        allRequiredKey = "chargeC.paymentDate.error.required",
        twoRequiredKey = "chargeC.paymentDate.error.incomplete",
        requiredKey = "chargeC.paymentDate.error.required"
      ).verifying(
        futureDate("chargeC.paymentDate.error.future"),
        yearHas4Digits("chargeC.paymentDate.error.invalid")
      ),
      "amountTaxDue" -> bigDecimal2DP(
        requiredKey = "chargeC.chargeAmount.error.required",
        invalidKey = "chargeC.chargeAmount.error.invalid",
        decimalKey = "chargeC.chargeAmount.error.decimal"
      ).verifying(
        maximumValue[BigDecimal](BigDecimal("9999999999.99"), "amountTaxDue.error.maximum"),
        minimumValue[BigDecimal](BigDecimal("0.00"), "chargeC.chargeAmount.error.invalid")
      )
    )(ChargeCDetails.apply)(ChargeCDetails.unapply))
}
