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

package controllers.financialStatement.paymentsAndCharges

import config.FrontendAppConfig
import controllers.actions._
import models.ChargeDetailsFilter.{All, Upcoming}
import models.financialStatement.PaymentOrChargeType.{AccountingForTaxCharges, getPaymentOrChargeType}
import models.financialStatement.{PaymentOrChargeType, SchemeFS}
import models.{ChargeDetailsFilter, Quarters}
import play.api.Logger
import play.api.i18n.{I18nSupport, Messages, MessagesApi}
import play.api.libs.json.Json
import play.api.mvc._
import renderer.Renderer
import services.paymentsAndCharges.PaymentsAndChargesService
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendBaseController
import uk.gov.hmrc.viewmodels.NunjucksSupport
import utils.DateHelper.{dateFormatterDMY, dateFormatterStartDate}
import viewmodels.Table

import java.time.LocalDate
import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

class PaymentsAndChargesController @Inject()(
                                              override val messagesApi: MessagesApi,
                                              identify: IdentifierAction,
                                              allowAccess: AllowAccessActionProviderForIdentifierRequest,
                                              val controllerComponents: MessagesControllerComponents,
                                              config: FrontendAppConfig,
                                              paymentsAndChargesService: PaymentsAndChargesService,
                                              renderer: Renderer
                                            )(implicit ec: ExecutionContext)
  extends FrontendBaseController
    with I18nSupport
    with NunjucksSupport {

  private val logger = Logger(classOf[PaymentsAndChargesController])

  def onPageLoad(srn: String, period: String, paymentOrChargeType: PaymentOrChargeType, journeyType: ChargeDetailsFilter): Action[AnyContent] =
    (identify andThen allowAccess()).async { implicit request =>
      paymentsAndChargesService.getPaymentsForJourney(request.idOrException, srn, journeyType).flatMap { paymentsCache =>

              val (title, filteredPayments): (String, Seq[SchemeFS]) =
                getTitleAndFilteredPayments(paymentsCache.schemeFS, period, paymentOrChargeType, journeyType)

              if (filteredPayments.nonEmpty) {

                val tableOfPaymentsAndCharges: Table = {
                  val table = paymentsAndChargesService.getPaymentsAndCharges(srn, filteredPayments, journeyType, paymentOrChargeType)
                  if(journeyType == Upcoming) removePaymentStatusColumn(table) else table
                }

                val json = Json.obj(
                  fields =
                    "titleMessage" -> title,
                    "paymentAndChargesTable" -> tableOfPaymentsAndCharges,
                    "schemeName" -> paymentsCache.schemeDetails.schemeName,
                    "returnUrl" -> config.schemeDashboardUrl(request).format(srn)
                )
                renderer.render(template = "financialStatement/paymentsAndCharges/paymentsAndCharges.njk", json).map(Ok(_))

              } else {
                logger.warn(s"No Scheme Payments and Charges returned for the selected period $period")
                Future.successful(Redirect(controllers.routes.SessionExpiredController.onPageLoad()))
              }

      }
  }

  private def getTitleAndFilteredPayments(payments: Seq[SchemeFS], period: String, paymentOrChargeType: PaymentOrChargeType, journeyType: ChargeDetailsFilter)
                                         (implicit messages: Messages): (String, Seq[SchemeFS]) =
    if(paymentOrChargeType == AccountingForTaxCharges) {
      val startDate: LocalDate = LocalDate.parse(period)
      (messages(s"paymentsAndCharges.$journeyType.aft.title",
        startDate.format(dateFormatterStartDate),
        Quarters.getQuarter(startDate).endDate.format(dateFormatterDMY)),
      payments.filter(p => getPaymentOrChargeType(p.chargeType) == AccountingForTaxCharges).filter(_.periodStartDate == startDate))
    } else {
      val typeParam: String = messages(s"paymentOrChargeType.${paymentOrChargeType.toString}")
      val messageParam: String = if(journeyType == All) typeParam else typeParam.toLowerCase
      (messages(s"paymentsAndCharges.$journeyType.nonAft.title", messageParam, period),
        payments.filter(p => getPaymentOrChargeType(p.chargeType) == paymentOrChargeType).filter(_.periodEndDate.getYear == period.toInt))
    }

  private val removePaymentStatusColumn: Table => Table = table =>
    Table(table.caption, table.captionClasses, table.firstCellIsHeader,
      table.head.take(table.head.size - 1),
      table.rows.map(p => p.take(p.size - 1)), table.classes, table.attributes
    )
}
