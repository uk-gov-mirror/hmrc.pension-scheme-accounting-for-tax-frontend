/*
 * Copyright 2021 HM Revenue & Customs
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

import models.{WithName, Enumerable}

sealed trait PsaFSChargeType

object PsaFSChargeType extends Enumerable.Implicits {

  case object AFT_INITIAL_LFP extends WithName("Accounting for Tax late filing penalty") with PsaFSChargeType
  case object AFT_DAILY_LFP extends WithName("Accounting for Tax further late filing penalty") with PsaFSChargeType
  case object AFT_30_DAY_LPP extends WithName("Accounting for Tax late payment penalty (30 days)") with PsaFSChargeType
  case object AFT_6_MONTH_LPP extends WithName("Accounting for Tax late payment penalty (6 months)") with PsaFSChargeType
  case object AFT_12_MONTH_LPP extends WithName("Accounting for Tax late payment penalty (12 months)") with PsaFSChargeType
  case object OTC_30_DAY_LPP extends WithName("Overseas transfer charge late payment penalty (30 days)") with PsaFSChargeType
  case object OTC_6_MONTH_LPP extends WithName("Overseas transfer charge late payment penalty (6 months)") with PsaFSChargeType
  case object OTC_12_MONTH_LPP extends WithName("Overseas transfer charge late payment penalty (12 months)") with PsaFSChargeType
  case object PSS_PENALTY extends WithName("Pensions Penalty") with PsaFSChargeType
  case object PSS_INFO_NOTICE extends WithName("Information Notice Penalty") with PsaFSChargeType
  case object CONTRACT_SETTLEMENT extends WithName("Contract settlement") with PsaFSChargeType
  case object CONTRACT_SETTLEMENT_INTEREST extends WithName("Contract settlement interest") with PsaFSChargeType

  val values: Seq[PsaFSChargeType] = Seq(
    AFT_INITIAL_LFP,
    AFT_DAILY_LFP,
    AFT_30_DAY_LPP,
    AFT_6_MONTH_LPP,
    AFT_12_MONTH_LPP,
    OTC_30_DAY_LPP,
    OTC_6_MONTH_LPP,
    OTC_12_MONTH_LPP,
    PSS_PENALTY,
    PSS_INFO_NOTICE,
    CONTRACT_SETTLEMENT,
    CONTRACT_SETTLEMENT_INTEREST
  )

  implicit val enumerable: Enumerable[PsaFSChargeType] =
  Enumerable(values.map(v => v.toString -> v): _*)
}
