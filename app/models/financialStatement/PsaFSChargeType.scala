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
import play.api.mvc.PathBindable

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
  case object PAYMENT_ON_ACCOUNT extends WithName("Payment on account") with PsaFSChargeType

  val values: Seq[PsaFSChargeType] = Seq(
    AFT_INITIAL_LFP,
    AFT_DAILY_LFP,
    AFT_30_DAY_LPP,
    AFT_6_MONTH_LPP,
    AFT_12_MONTH_LPP,
    OTC_30_DAY_LPP,
    OTC_6_MONTH_LPP,
    OTC_12_MONTH_LPP,
    PAYMENT_ON_ACCOUNT
  )

  implicit val enumerable: Enumerable[PsaFSChargeType] =
  Enumerable(values.map(v => v.toString -> v): _*)

  implicit def chargeTypePathBindable(implicit stringBinder: PathBindable[String]): PathBindable[PsaFSChargeType] =
    new PathBindable[PsaFSChargeType] {

    override def bind(key: String, value: String): Either[String, PsaFSChargeType] =
      stringBinder.bind(key, value) match {
        case Right("AFT_INITIAL_LFP") => Right(AFT_INITIAL_LFP)
        case Right("AFT_DAILY_LFP") => Right(AFT_DAILY_LFP)
        case Right("AFT_30_DAY_LPP") => Right(AFT_30_DAY_LPP)
        case Right("AFT_6_MONTH_LPP") => Right(AFT_6_MONTH_LPP)
        case Right("AFT_12_MONTH_LPP") => Right(AFT_12_MONTH_LPP)
        case Right("OTC_30_DAY_LPP") => Right(OTC_30_DAY_LPP)
        case Right("OTC_6_MONTH_LPP") => Right(OTC_6_MONTH_LPP)
        case Right("OTC_12_MONTH_LPP") => Right(OTC_12_MONTH_LPP)
        case Right("PAYMENT_ON_ACCOUNT") => Right(PAYMENT_ON_ACCOUNT)
        case _ => Left("AccessType binding failed")
      }

    override def unbind(key: String, value: PsaFSChargeType): String = {
      val chargeTypeValue = values.find(_ == value).map(_.toString).getOrElse(throw UnknownChargeTypeException())
      stringBinder.unbind(key, chargeTypeValue)
    }
  }

  case class UnknownChargeTypeException() extends Exception
}