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

import connectors.cache.FinancialInfoCacheConnector
import controllers.chargeB.{routes => _}
import helpers.FormatHelper._
import javax.inject.Inject
import models.LocalDateBinder._
import models.financialStatement.SchemeFS
import models.financialStatement.SchemeFSChargeType.{PSS_AFT_RETURN, PSS_AFT_RETURN_INTEREST, PSS_OTC_AFT_RETURN, PSS_OTC_AFT_RETURN_INTEREST}
import models.viewModels.paymentsAndCharges.PaymentAndChargeStatus.{InterestIsAccruing, NoStatus, PaymentOverdue}
import models.viewModels.paymentsAndCharges.{PaymentsAndChargesDetails, PaymentsAndChargesTable}
import play.api.i18n.Messages
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.viewmodels.SummaryList.{Key, Row, Value}
import uk.gov.hmrc.viewmodels.Text.Literal
import uk.gov.hmrc.viewmodels.{Content, Html, SummaryList, _}
import utils.DateHelper.{dateFormatterDMY, dateFormatterStartDate}
import viewmodels.Table
import viewmodels.Table.Cell

import scala.concurrent.{ExecutionContext, Future}

class PaymentsAndChargesService @Inject()(fiCacheConnector: FinancialInfoCacheConnector) {

  def getPaymentsAndCharges(paymentsAndChargesForAGivenPeriod: Seq[(LocalDate, Seq[SchemeFS])], srn: String)
                           (implicit messages: Messages, ec: ExecutionContext, hc: HeaderCarrier): Future[Seq[PaymentsAndChargesTable]] = {

    fiCacheConnector.fetch map {
      case Some(jsValue) =>
        val chargeRefs = (jsValue \ "chargeRefs").as[Seq[String]]

        paymentsAndChargesForAGivenPeriod.map {
          paymentsAndCharges =>

            val seqPaymentsAndCharges: Seq[SchemeFS] = paymentsAndCharges._2

            val seqPayments: Seq[PaymentsAndChargesDetails] =
              seqPaymentsAndCharges.flatMap {
                details =>
                  val onlyAFTAndOTCChargeTypes: Boolean =
                    details.chargeType == PSS_AFT_RETURN || details.chargeType == PSS_OTC_AFT_RETURN

                  val redirectChargeDetailsUrl: String =
                    controllers.paymentsAndCharges.routes.PaymentsAndChargeDetailsController
                      .onPageLoad(srn, details.periodStartDate, chargeRefs.indexOf(details.chargeReference).toString)
                      .url

                  (onlyAFTAndOTCChargeTypes, details.amountDue > 0) match {

                    case (true, true) if details.accruedInterestTotal > 0 =>
                      val interestChargeType =
                        if (details.chargeType == PSS_AFT_RETURN) PSS_AFT_RETURN_INTEREST else PSS_OTC_AFT_RETURN_INTEREST
                      Seq(
                        PaymentsAndChargesDetails(
                          chargeType = details.chargeType.toString,
                          chargeReference = details.chargeReference,
                          amountDue = s"${formatCurrencyAmountAsString(details.amountDue)}",
                          status = PaymentOverdue,
                          redirectUrl = redirectChargeDetailsUrl,
                          visuallyHiddenText = messages(s"paymentsAndCharges.visuallyHiddenText", details.chargeReference)
                        ),
                        PaymentsAndChargesDetails(
                          chargeType = interestChargeType.toString,
                          chargeReference = messages("paymentsAndCharges.chargeReference.toBeAssigned"),
                          amountDue = s"${formatCurrencyAmountAsString(details.accruedInterestTotal)}",
                          status = InterestIsAccruing,
                          redirectUrl = controllers.paymentsAndCharges.routes.PaymentsAndChargesInterestController
                            .onPageLoad(srn, details.periodStartDate, chargeRefs.indexOf(details.chargeReference).toString)
                            .url,
                          visuallyHiddenText = messages(s"paymentsAndCharges.interest.visuallyHiddenText")
                        )
                      )
                    case (true, _) if details.totalAmount < 0 =>
                      Seq.empty
                    case _ =>
                      Seq(
                        PaymentsAndChargesDetails(
                          chargeType = details.chargeType.toString,
                          chargeReference = details.chargeReference,
                          amountDue = s"${formatCurrencyAmountAsString(details.amountDue)}",
                          status = NoStatus,
                          redirectUrl = redirectChargeDetailsUrl,
                          visuallyHiddenText = messages(s"paymentsAndCharges.visuallyHiddenText", details.chargeReference)
                        )
                      )
                  }
              }

            val startDate = seqPaymentsAndCharges.headOption.map(_.periodStartDate.format(dateFormatterStartDate)).getOrElse("")
            val endDate = seqPaymentsAndCharges.headOption.map(_.periodEndDate.format(dateFormatterDMY)).getOrElse("")
            mapToTable(startDate, endDate, seqPayments)
        }
      case _ =>
        Seq.empty[PaymentsAndChargesTable]
    }
  }

  private def htmlStatus(data: PaymentsAndChargesDetails)
                        (implicit messages: Messages): Html = {
    val (classes, content) = (data.status, data.amountDue) match {
      case (InterestIsAccruing, _) => ("govuk-tag govuk-tag--blue", data.status.toString)
      case (PaymentOverdue, _) => ("govuk-tag govuk-tag--red", data.status.toString)
      case (_, "£0.00") =>
        ("govuk-visually-hidden", messages("paymentsAndCharges.chargeDetails.visuallyHiddenText.noPaymentDue"))
      case _ => ("govuk-visually-hidden", messages("paymentsAndCharges.chargeDetails.visuallyHiddenText.paymentIsDue"))
    }
    Html(s"<span class='$classes'>$content</span>")
  }

  private def mapToTable(startDate: String, endDate: String, allPayments: Seq[PaymentsAndChargesDetails])
                        (implicit messages: Messages): PaymentsAndChargesTable = {
    val caption = messages("paymentsAndCharges.caption", startDate, endDate)

    val head = Seq(
      Cell(msg"paymentsAndCharges.chargeType.table", classes = Seq("govuk-!-width-two-thirds-quarter")),
      Cell(msg"paymentsAndCharges.totalDue.table", classes = Seq("govuk-!-width-one-quarter", "govuk-!-font-weight-bold")),
      Cell(msg"paymentsAndCharges.chargeReference.table", classes = Seq("govuk-!-width-one-quarter", "govuk-!-font-weight-bold")),
      Cell(Html(s"<span class='govuk-visually-hidden'>${messages("paymentsAndCharges.chargeDetails.paymentStatus")}</span>"))
    )

    val rows = allPayments.map { data =>
      val linkId =
        data.chargeReference match {
          case "To be assigned" => "to-be-assigned"
          case "None" => "none"
          case _ => data.chargeReference
        }

      val htmlChargeType = Html(
        s"<a id=$linkId class=govuk-link href=" +
          s"${data.redirectUrl}>" +
          s"${data.chargeType} " +
          s"<span class=govuk-visually-hidden>${data.visuallyHiddenText}</span> </a>")

      Seq(
        Cell(htmlChargeType, classes = Seq("govuk-!-width-two-thirds-quarter")),
        Cell(Literal(data.amountDue), classes = Seq("govuk-!-width-one-quarter")),
        Cell(Literal(s"${data.chargeReference}"), classes = Seq("govuk-!-width-one-quarter")),
        Cell(htmlStatus(data), classes = Nil)
      )
    }

    PaymentsAndChargesTable(
      caption,
      Table(
        head = head,
        rows = rows
      )
    )
  }

  def getChargeDetailsForSelectedCharge(schemeFS: SchemeFS)
                                       (implicit messages: Messages): Seq[SummaryList.Row] = {
    originalAmountChargeDetailsRow(schemeFS) ++ paymentsAndCreditsChargeDetailsRow(schemeFS) ++
      stoodOverAmountChargeDetailsRow(schemeFS) ++ totalAmountDueChargeDetailsRow(schemeFS)
  }

  private def originalAmountChargeDetailsRow(schemeFS: SchemeFS)
                                            (implicit messages: Messages): Seq[SummaryList.Row] = {
    val value = if (schemeFS.totalAmount < 0) {
      s"${formatCurrencyAmountAsString(schemeFS.totalAmount.abs)} ${messages("paymentsAndCharges.credit")}"
    } else {
      s"${formatCurrencyAmountAsString(schemeFS.totalAmount)}"
    }
    Seq(
      Row(
        key = Key(msg"paymentsAndCharges.chargeDetails.originalChargeAmount", classes = Seq("govuk-!-padding-left-0", "govuk-!-width-three-quarters")),
        value = Value(
          Literal(value),
          classes = if (schemeFS.totalAmount < 0) Nil else Seq("govuk-!-width-one-quarter", "govuk-table__cell--numeric")
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
            Literal(s"-${formatCurrencyAmountAsString(schemeFS.totalAmount - schemeFS.amountDue - schemeFS.stoodOverAmount)}"),
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
            Literal(s"-${formatCurrencyAmountAsString(schemeFS.stoodOverAmount)}"),
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
      case _ => msg"paymentsAndCharges.chargeDetails.noAmountDueDate"
    }
    if (schemeFS.totalAmount > 0) {
      Seq(
        Row(
          key =
            Key(amountDueKey,
              classes = Seq("govuk-table__cell--numeric", "govuk-!-padding-right-0", "govuk-!-width-three-quarters", "govuk-!-font-weight-bold")),
          value = Value(
            Literal(s"${formatCurrencyAmountAsString(schemeFS.amountDue)}"),
            classes = Seq("govuk-!-width-one-quarter", "govuk-table__cell--numeric", "govuk-!-font-weight-bold")
          ),
          actions = Nil
        ))
    } else {
      Nil
    }
  }
}
