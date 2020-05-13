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

package controllers.chargeF

import java.time.LocalDate

import config.FrontendAppConfig
import connectors.cache.UserAnswersCacheConnector
import controllers.DataRetrievals
import controllers.actions._
import forms.chargeF.ChargeDetailsFormProvider
import javax.inject.Inject
import models.LocalDateBinder._
import models.SessionData
import models.chargeF.ChargeDetails
import models.GenericViewModel
import models.Mode
import models.Quarters
import navigators.CompoundNavigator
import pages.chargeF.ChargeDetailsPage
import play.api.data.Form
import play.api.i18n.I18nSupport
import play.api.i18n.Messages
import play.api.i18n.MessagesApi
import play.api.libs.json.Json
import play.api.mvc.Action
import play.api.mvc.AnyContent
import play.api.mvc.MessagesControllerComponents
import renderer.Renderer
import services.UserAnswersService
import uk.gov.hmrc.play.bootstrap.controller.FrontendBaseController
import uk.gov.hmrc.viewmodels.DateInput
import uk.gov.hmrc.viewmodels.NunjucksSupport
import utils.DeleteChargeHelper

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

class ChargeDetailsController @Inject()(override val messagesApi: MessagesApi,
                                        userAnswersCacheConnector: UserAnswersCacheConnector,
                                        userAnswersService: UserAnswersService,
                                        navigator: CompoundNavigator,
                                        identify: IdentifierAction,
                                        getData: DataRetrievalAction,
                                        allowAccess: AllowAccessActionProvider,
                                        requireData: DataRequiredAction,
                                        formProvider: ChargeDetailsFormProvider,
                                        val controllerComponents: MessagesControllerComponents,
                                        deleteChargeHelper: DeleteChargeHelper,
                                        config: FrontendAppConfig,
                                        renderer: Renderer)(implicit ec: ExecutionContext)
    extends FrontendBaseController
    with I18nSupport
    with NunjucksSupport {

  private def form(minimumChargeValue:BigDecimal, startDate: LocalDate)(implicit messages: Messages): Form[ChargeDetails] = {
    val endDate = Quarters.getQuarter(startDate).endDate
    formProvider(
      startDate,
      endDate,
      minimumChargeValue
    )
  }

  def onPageLoad(mode: Mode, srn: String, startDate: LocalDate): Action[AnyContent] =
    (identify andThen getData(srn, startDate) andThen requireData andThen allowAccess(srn, startDate)).async { implicit request =>
      DataRetrievals.retrieveSchemeName { schemeName =>

        val mininimumChargeValue:BigDecimal = request.sessionData.deriveMinimumChargeValueAllowed
        def shouldPrepop(chargeDetails: ChargeDetails): Boolean =
          chargeDetails.totalAmount > BigDecimal(0.00) || deleteChargeHelper.isLastCharge(request.userAnswers)

        val preparedForm: Form[ChargeDetails] = request.userAnswers.get(ChargeDetailsPage) match {
          case Some(value) if shouldPrepop(value) => form(mininimumChargeValue, startDate).fill(value)
          case _        => form(mininimumChargeValue, startDate)
        }

        val viewModel = GenericViewModel(
          submitUrl = routes.ChargeDetailsController.onSubmit(mode, srn, startDate).url,
          returnUrl = controllers.routes.ReturnToSchemeDetailsController.returnToSchemeDetails(srn, startDate).url,
          schemeName = schemeName
        )

        val json = Json.obj(
          "srn" -> srn,
          "startDate" -> Some(startDate),
          "form" -> preparedForm,
          "viewModel" -> viewModel,
          "date" -> DateInput.localDate(preparedForm("deregistrationDate"))
        )

        renderer.render(template = "chargeF/chargeDetails.njk", json).map(Ok(_))
      }
    }

  def onSubmit(mode: Mode, srn: String, startDate: LocalDate): Action[AnyContent] =
    (identify andThen getData(srn, startDate) andThen requireData).async { implicit request =>
      DataRetrievals.retrieveSchemeName { schemeName =>

        val mininimumChargeValue:BigDecimal = request.sessionData.deriveMinimumChargeValueAllowed

        form(mininimumChargeValue, startDate)
          .bindFromRequest()
          .fold(
            formWithErrors => {
              val viewModel = GenericViewModel(
                submitUrl = routes.ChargeDetailsController.onSubmit(mode, srn, startDate).url,
                returnUrl = controllers.routes.ReturnToSchemeDetailsController.returnToSchemeDetails(srn, startDate).url,
                schemeName = schemeName
              )

              val json = Json.obj(
                "srn" -> srn,
                "startDate" -> Some(startDate),
                "form" -> formWithErrors,
                "viewModel" -> viewModel,
                "date" -> DateInput.localDate(formWithErrors("deregistrationDate"))
              )
              renderer.render(template = "chargeF/chargeDetails.njk", json).map(BadRequest(_))
            },
            value => {
              for {
                updatedAnswers <- Future.fromTry(userAnswersService.set(ChargeDetailsPage, value, mode, isMemberBased = false))
                _ <- userAnswersCacheConnector.save(request.internalId, updatedAnswers.data)
              } yield Redirect(navigator.nextPage(ChargeDetailsPage, mode, updatedAnswers, srn, startDate))
            }
          )
      }
    }
}
