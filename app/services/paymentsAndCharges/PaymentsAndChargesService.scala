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

package services.paymentsAndCharges

import java.time.LocalDate

import controllers.chargeB.{routes => _}
import helpers.FormatHelper
import models.LocalDateBinder._
import models.financialStatement.SchemeFS
import models.financialStatement.SchemeFSChargeType.{PSS_AFT_RETURN, PSS_AFT_RETURN_INTEREST, PSS_OTC_AFT_RETURN, PSS_OTC_AFT_RETURN_INTEREST}
import models.viewModels.paymentsAndCharges.PaymentAndChargeStatus.{InterestIsAccruing, NoStatus, PaymentOverdue}
import models.viewModels.paymentsAndCharges.{PaymentsAndChargesDetails, PaymentsAndChargesTable}
import play.api.i18n.Messages
import uk.gov.hmrc.viewmodels.SummaryList.{Key, Row, Value}
import uk.gov.hmrc.viewmodels.Text.Literal
import uk.gov.hmrc.viewmodels.{Content, Html, SummaryList, _}
import utils.DateHelper.{dateFormatterDMY, dateFormatterStartDate}
import viewmodels.Table
import viewmodels.Table.Cell

class PaymentsAndChargesService {

  def getPaymentsAndChargesSeqOfTables(paymentsAndChargesForAGivenPeriod: Seq[(LocalDate, Seq[SchemeFS])], srn: String)(
      implicit messages: Messages): Seq[PaymentsAndChargesTable] = {

    paymentsAndChargesForAGivenPeriod.map { paymentsAndCharges =>
      val seqPaymentsAndCharges = paymentsAndCharges._2

      val seqPayments = seqPaymentsAndCharges.flatMap { details =>
        val onlyAFTAndOTCChargeTypes = details.chargeType == PSS_AFT_RETURN || details.chargeType == PSS_OTC_AFT_RETURN

        val redirectChargeDetailsUrl = controllers.paymentsAndCharges.routes.PaymentsAndChargeDetailsController
          .onPageLoad(srn, details.periodStartDate, details.chargeReference)
          .url

        (onlyAFTAndOTCChargeTypes, details.amountDue > 0) match {

          case (true, true) if details.accruedInterestTotal > 0 =>
            createPaymentAndChargesIfInterestAccrued(details, srn)

          case (true, _) if details.totalAmount < 0 =>
            Seq(
              PaymentsAndChargesDetails(
                details.chargeType.toString,
                messages("paymentsAndCharges.chargeReference.None"),
                messages("paymentsAndCharges.amountDue.in.credit"),
                NoStatus,
                redirectChargeDetailsUrl
              ))

          case _ =>
            Seq(
              PaymentsAndChargesDetails(
                details.chargeType.toString,
                details.chargeReference,
                s"${FormatHelper.formatCurrencyAmountAsString(details.amountDue)}",
                NoStatus,
                redirectChargeDetailsUrl
              ))
        }
      }

      val startDate = seqPaymentsAndCharges.headOption.map(_.periodStartDate.format(dateFormatterStartDate)).getOrElse("")
      val endDate = seqPaymentsAndCharges.headOption.map(_.periodEndDate.format(dateFormatterDMY)).getOrElse("")

      mapToTable(startDate, endDate, seqPayments, srn)
    }
  }

  private def createPaymentAndChargesIfInterestAccrued(details: SchemeFS, srn: String)(
      implicit messages: Messages): Seq[PaymentsAndChargesDetails] = {
    val interestChargeType = if (details.chargeType == PSS_AFT_RETURN) PSS_AFT_RETURN_INTEREST else PSS_OTC_AFT_RETURN_INTEREST
    val redirectUrl = controllers.paymentsAndCharges.routes.PaymentsAndChargeDetailsController
      .onPageLoad(srn, details.periodStartDate, details.chargeReference)
      .url

    Seq(
      PaymentsAndChargesDetails(
        details.chargeType.toString,
        details.chargeReference,
        s"${FormatHelper.formatCurrencyAmountAsString(details.amountDue)}",
        PaymentOverdue,
        redirectUrl
      ),
      PaymentsAndChargesDetails(
        interestChargeType.toString,
        messages("paymentsAndCharges.chargeReference.toBeAssigned"),
        s"${FormatHelper.formatCurrencyAmountAsString(details.accruedInterestTotal)}",
        InterestIsAccruing,
        redirectUrl
      )
    )
  }

  private def mapToTable(startDate: String, endDate: String, allPayments: Seq[PaymentsAndChargesDetails], srn: String)(
      implicit messages: Messages): PaymentsAndChargesTable = {

    val caption = messages("paymentsAndCharges.caption", startDate, endDate)

    val head = Seq(
      Cell(msg"paymentsAndCharges.chargeType.table", classes = Seq("govuk-!-width-two-thirds-quarter")),
      Cell(msg"paymentsAndCharges.totalDue.table", classes = Seq("govuk-!-width-one-quarter", "govuk-!-font-weight-bold")),
      Cell(msg"paymentsAndCharges.chargeReference.table", classes = Seq("govuk-!-width-one-quarter", "govuk-!-font-weight-bold")),
      Cell(msg"", classes = Seq("govuk-!-font-weight-bold"))
    )

    val rows = allPayments.map { data =>
      val htmlStatus = data.status match {
        case InterestIsAccruing => Html(s"<span class='govuk-tag govuk-tag--blue'>${data.status.toString}</span>")
        case PaymentOverdue     => Html(s"<span class='govuk-tag govuk-tag--red'>${data.status.toString}</span>")
        case _                  => Html("")
      }

      val htmlChargeType = Html(
        s"<a id=linkId class=govuk-link href=" +
          s"${data.redirectUrl}>" +
          s"${data.chargeType}" +
          s"<span class=govuk-visually-hidden>${messages(s"paymentsAndCharges.visuallyHiddenText", data.chargeReference)}</span> </a>")

      Seq(
        Cell(htmlChargeType, classes = Seq("govuk-!-width-two-thirds-quarter")),
        Cell(Literal(data.amountDue), classes = Seq("govuk-!-width-one-quarter")),
        Cell(Literal(s"${data.chargeReference}"), classes = Seq("govuk-!-width-one-quarter")),
        Cell(htmlStatus, classes = Nil)
      )
    }

    PaymentsAndChargesTable(
      caption,
      Table(
        head = head,
        rows = rows,
        attributes = Map("role" -> "grid", "aria-describedby" -> caption)
      )
    )
  }

  def getChargeDetailsForSelectedCharge(schemeFS: SchemeFS)(implicit messages: Messages): Seq[SummaryList.Row] = {
    originalAmountChargeDetailsRow(schemeFS) ++ paymentsAndCreditsChargeDetailsRow(schemeFS) ++
      stoodOverAmountChargeDetailsRow(schemeFS) ++ totalAmountDueChargeDetailsRow(schemeFS)
  }

  private def originalAmountChargeDetailsRow(schemeFS: SchemeFS)(implicit messages: Messages): Seq[SummaryList.Row] = {
    val credit = if (schemeFS.totalAmount < 0) messages("paymentsAndCharges.credit") else ""
    Seq(
      Row(
        key = Key(msg"paymentsAndCharges.chargeDetails.originalChargeAmount", classes = Seq("govuk-!-padding-left-0", "govuk-!-width-three-quarters")),
        value = Value(
          Literal(s"${FormatHelper.formatCurrencyAmountAsString(schemeFS.totalAmount.abs)} $credit"),
          classes = Seq(if (schemeFS.totalAmount < 0) "" else "govuk-!-width-one-quarter", "govuk-table__cell--numeric")
        ),
        actions = Nil
      ))
  }

  private def paymentsAndCreditsChargeDetailsRow(schemeFS: SchemeFS): Seq[SummaryList.Row] = {
    if (schemeFS.totalAmount - schemeFS.amountDue - schemeFS.stoodOverAmount > 0) {
      Seq(
        Row(
          key = Key(msg"paymentsAndCharges.chargeDetails.payments", classes = Seq("govuk-!-padding-left-0", "govuk-!-width-three-quarters")),
          value = Value(
            Literal(s"-${FormatHelper.formatCurrencyAmountAsString(schemeFS.totalAmount - schemeFS.amountDue - schemeFS.stoodOverAmount)}"),
            classes = Seq("govuk-!-width-one-quarter", "govuk-table__cell--numeric")
          ),
          actions = Nil
        ))
    } else {
      Nil
    }
  }

  private def stoodOverAmountChargeDetailsRow(schemeFS: SchemeFS): Seq[SummaryList.Row] = {
    if (schemeFS.stoodOverAmount > 0) {
      Seq(
        Row(
          key = Key(msg"paymentsAndCharges.chargeDetails.stoodOverAmount", classes = Seq("govuk-!-padding-left-0", "govuk-!-width-three-quarters")),
          value = Value(
            Literal(s"-${FormatHelper.formatCurrencyAmountAsString(schemeFS.stoodOverAmount)}"),
            classes = Seq("govuk-!-width-one-quarter", "govuk-table__cell--numeric")
          ),
          actions = Nil
        ))
    } else {
      Nil
    }
  }

  private def totalAmountDueChargeDetailsRow(schemeFS: SchemeFS): Seq[SummaryList.Row] = {
    val amountDueKey: Content = (schemeFS.dueDate, schemeFS.amountDue > 0) match {
      case (Some(date), true) => msg"paymentsAndCharges.chargeDetails.amountDue".withArgs(date.format(dateFormatterDMY))
      case _                  => msg"paymentsAndCharges.chargeDetails.noAmountDue"
    }
    if (schemeFS.totalAmount > 0) {
      Seq(
        Row(
          key =
            Key(amountDueKey,
                classes = Seq("govuk-table__cell--numeric", "govuk-!-padding-right-0", "govuk-!-width-three-quarters", "govuk-!-font-weight-bold")),
          value = Value(
            Literal(s"${FormatHelper.formatCurrencyAmountAsString(schemeFS.amountDue)}"),
            classes = Seq("govuk-!-width-one-quarter", "govuk-table__cell--numeric", "govuk-!-font-weight-bold")
          ),
          actions = Nil
        ))
    } else {
      Nil
    }
  }
}
