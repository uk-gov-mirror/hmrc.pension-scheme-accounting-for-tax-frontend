/*
 * Copyright 2019 HM Revenue & Customs
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

import config.FrontendAppConfig
import connectors.cache.UserAnswersCacheConnector
import controllers.actions._
import forms.ChargeDetailsFormProvider
import javax.inject.Inject
import models.chargeF.ChargeDetails
import models.{Mode, UserAnswers}
import navigators.CompoundNavigator
import pages.{ChargeDetailsPage, SchemeNameQuery}
import play.api.data.Form
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.libs.json.Json
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents}
import renderer.Renderer
import uk.gov.hmrc.play.bootstrap.controller.FrontendBaseController
import uk.gov.hmrc.viewmodels.{DateInput, NunjucksSupport}

import scala.concurrent.{ExecutionContext, Future}

class ChargeDetailsController @Inject()(override val messagesApi: MessagesApi,
                                        userAnswersCacheConnector: UserAnswersCacheConnector,
                                        navigator: CompoundNavigator,
                                        identify: IdentifierAction,
                                        getData: DataRetrievalAction,
                                        requireData: DataRequiredAction,
                                        formProvider: ChargeDetailsFormProvider,
                                        val controllerComponents: MessagesControllerComponents,
                                        config: FrontendAppConfig,
                                        renderer: Renderer
                                       )(implicit ec: ExecutionContext) extends FrontendBaseController with I18nSupport with NunjucksSupport {

  val form = formProvider()

  def onPageLoad(mode: Mode, srn: String): Action[AnyContent] = (identify andThen getData).async {
    implicit request =>

      val ua = request.userAnswers.getOrElse(UserAnswers(Json.obj()))

      val preparedForm: Form[ChargeDetails] = ua.get(ChargeDetailsPage) match {
        case Some(value) => form.fill(value)
        case None => form
      }

      val schemeName: String = ua.get(SchemeNameQuery).getOrElse("")

      val viewModel: DateInput.ViewModel = DateInput.localDate(preparedForm("deregistrationDate"))

//      val vm: Option[ChargeDetailsViewModel] = ua.get(SchemeNameQuery).map {
//        sm =>
//          ChargeDetailsViewModel(
//            form = preparedForm,
//            submitUrl = routes.ChargeDetailsController.onSubmit(mode, srn).url,
//            returnUrl = config.managePensionsSchemeSummaryUrl.format(srn),
//            date = viewModel,
//            schemeName = sm
//          )
//      }

      val json = Json.obj(
        "form" -> preparedForm,
        "submitUrl" -> routes.ChargeDetailsController.onSubmit(mode, srn).url,
        "returnUrl" -> config.managePensionsSchemeSummaryUrl.format(srn),
        "date" -> viewModel,
        "schemeName" -> schemeName
      )

      renderer.render(template = "chargeF/chargeDetails.njk", json).map(Ok(_))
//      renderer.render(template = "chargeF/chargeDetails.njk", vm.get).map(Ok(_))
  }

  def onSubmit(mode: Mode, srn: String): Action[AnyContent] = (identify andThen getData).async {
    implicit request =>

      val ua = request.userAnswers.getOrElse(UserAnswers(Json.obj()))
      val schemeName = ua.get(SchemeNameQuery).getOrElse("")

      form.bindFromRequest().fold(
        formWithErrors => {

          val viewModel = DateInput.localDate(formWithErrors("deregistrationDate"))

          val json = Json.obj(
            "form" -> formWithErrors,
            "submitUrl" -> routes.ChargeDetailsController.onSubmit(mode, srn).url,
            "returnUrl" -> config.managePensionsSchemeSummaryUrl.format(srn),
            "date" -> viewModel,
            "schemeName" -> schemeName
          )

          renderer.render("chargeF/chargeDetails.njk", json).map(BadRequest(_))
        },
        value => {
          for {
            updatedAnswers <- Future.fromTry(ua.set(ChargeDetailsPage, value))
            _ <- userAnswersCacheConnector.save(request.internalId, updatedAnswers.data)
          } yield Redirect(navigator.nextPage(ChargeDetailsPage, mode, updatedAnswers, srn))
        }
      )
  }
}
