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
import forms.YearsFormProvider
import models.financialStatement.PaymentOrChargeType.{AccountingForTaxCharges, ExcessReliefPaidCharges, InterestOnExcessRelief, getPaymentOrChargeType}
import models.financialStatement.{PaymentOrChargeType, SchemeFS}
import models.{ChargeDetailsFilter, DisplayYear, FSYears, PaymentOverdue, Year}
import play.api.data.Form
import play.api.i18n.{I18nSupport, Messages, MessagesApi}
import play.api.libs.json.Json
import play.api.mvc._
import renderer.Renderer
import services.paymentsAndCharges.{PaymentsAndChargesService, PaymentsNavigationService}
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendBaseController
import uk.gov.hmrc.viewmodels.NunjucksSupport

import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

class SelectYearController @Inject()(override val messagesApi: MessagesApi,
                                     identify: IdentifierAction,
                                     allowAccess: AllowAccessActionProviderForIdentifierRequest,
                                     formProvider: YearsFormProvider,
                                     val controllerComponents: MessagesControllerComponents,
                                     renderer: Renderer,
                                     config: FrontendAppConfig,
                                     service: PaymentsAndChargesService,
                                     navService: PaymentsNavigationService)
                                    (implicit ec: ExecutionContext) extends FrontendBaseController
  with I18nSupport
  with NunjucksSupport {

  private def form(journeyType: ChargeDetailsFilter, paymentOrChargeType: PaymentOrChargeType, typeParam: String, config: FrontendAppConfig)
                  (implicit messages: Messages): Form[Year] = {
    val errorMessage = if(isTaxYearFormat(paymentOrChargeType)) {
      messages(s"selectChargesTaxYear.$journeyType.error", typeParam)
    } else {
      messages(s"selectChargesYear.$journeyType.error", typeParam)
    }
    formProvider(errorMessage)(config)
  }

  def onPageLoad(srn: String, paymentOrChargeType: PaymentOrChargeType, journeyType: ChargeDetailsFilter): Action[AnyContent] =
    (identify andThen allowAccess()).async { implicit request =>

    service.getPaymentsForJourney(request.idOrException, srn, journeyType).flatMap { paymentsCache =>

      val typeParam: String = service.getTypeParam(paymentOrChargeType)
      val years = getYears(paymentsCache.schemeFS, paymentOrChargeType)
      val json = Json.obj(
        "titleMessage" -> getTitle(typeParam, paymentOrChargeType, journeyType),
        "typeParam" -> typeParam,
        "schemeName" -> paymentsCache.schemeDetails.schemeName,
        "form" -> form(journeyType, paymentOrChargeType, typeParam, config),
        "radios" -> FSYears.radios(form(journeyType, paymentOrChargeType, typeParam, config), years, isTaxYearFormat(paymentOrChargeType)),
        "returnUrl" -> config.schemeDashboardUrl(request).format(srn)
      )

      renderer.render(template = "financialStatement/paymentsAndCharges/selectYear.njk", json).map(Ok(_))
    }
  }

  def onSubmit(srn: String, paymentOrChargeType: PaymentOrChargeType, journeyType: ChargeDetailsFilter): Action[AnyContent] =
    identify.async { implicit request =>
    service.getPaymentsForJourney(request.idOrException, srn, journeyType).flatMap { paymentsCache =>
      val typeParam: String = service.getTypeParam(paymentOrChargeType)
      form(journeyType, paymentOrChargeType, typeParam, config).bindFromRequest().fold(
        formWithErrors => {

          val json = Json.obj(
            "titleMessage" -> getTitle(typeParam, paymentOrChargeType, journeyType),
            "typeParam" -> typeParam,
            "schemeName" -> paymentsCache.schemeDetails.schemeName,
            "form" -> formWithErrors,
            "radios" -> FSYears.radios(formWithErrors, getYears(paymentsCache.schemeFS, paymentOrChargeType), isTaxYearFormat(paymentOrChargeType)),
            "returnUrl" -> config.schemeDashboardUrl(request).format(srn)
          )
          renderer.render(template = "financialStatement/paymentsAndCharges/selectYear.njk", json).map(BadRequest(_))
        },
        value => if(paymentOrChargeType == AccountingForTaxCharges) {
          navService.navFromAFTYearsPage(paymentsCache.schemeFS, value.year, srn, journeyType)
        } else {
          Future.successful(Redirect(routes.PaymentsAndChargesController.onPageLoad(srn, value.year.toString, paymentOrChargeType, journeyType)))
        }
      )
    }
  }

  def getTitle(typeParam: String, paymentOrChargeType: PaymentOrChargeType, journeyType: ChargeDetailsFilter)
              (implicit messages: Messages): String =
    if(isTaxYearFormat(paymentOrChargeType)) {
      messages(s"selectChargesTaxYear.$journeyType.title", typeParam)
    } else {
      messages(s"selectChargesYear.$journeyType.title", typeParam)
    }

  def getYears(payments: Seq[SchemeFS], paymentOrChargeType: PaymentOrChargeType): Seq[DisplayYear] =
    payments
      .filter(p => getPaymentOrChargeType(p.chargeType) == paymentOrChargeType)
      .map(_.periodEndDate.getYear).distinct.sorted.reverse.map { year =>
      val hint = if (payments.filter(_.periodEndDate.getYear == year).exists(service.isPaymentOverdue)) Some(PaymentOverdue) else None
      DisplayYear(year, hint)
    }

  val isTaxYearFormat: PaymentOrChargeType => Boolean = ct => ct == InterestOnExcessRelief || ct == ExcessReliefPaidCharges

}
