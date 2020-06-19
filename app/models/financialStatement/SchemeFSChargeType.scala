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

package models.financialStatement

import models.{Enumerable, WithName}

sealed trait SchemeFSChargeType


object SchemeFSChargeType extends Enumerable.Implicits {

  case object PAYMENT_ON_ACCOUNT extends WithName("Payment on Account") with SchemeFSChargeType
  case object PSS_AFT_RETURN extends WithName("PSS AFT Return") with SchemeFSChargeType
  case object PSS_AFT_RETURN_INTEREST extends WithName("PSS AFT Return Interest") with SchemeFSChargeType
  case object PSS_OTC_AFT_RETURN extends WithName("PSS OTC AFT Return") with SchemeFSChargeType
  case object PSS_OTC_AFT_RETURN_INTEREST extends WithName("PSS OTC AFT Return Interest") with SchemeFSChargeType

  val values: Seq[SchemeFSChargeType] = Seq(
    PAYMENT_ON_ACCOUNT,
    PSS_AFT_RETURN,
    PSS_AFT_RETURN_INTEREST,
    PSS_OTC_AFT_RETURN,
    PSS_OTC_AFT_RETURN_INTEREST
  )

  implicit val enumerable: Enumerable[SchemeFSChargeType] =
    Enumerable(values.map(v => v.toString -> v): _*)
}