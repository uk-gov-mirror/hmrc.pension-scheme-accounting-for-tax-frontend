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

package controllers

import config.FrontendAppConfig
import connectors.cache.UserAnswersCacheConnector
import controllers.actions._
import forms.ConfirmSubmitAFTReturnFormProvider
import javax.inject.Inject
import models.{Mode, GenericViewModel}
import navigators.CompoundNavigator
import pages.ConfirmSubmitAFTReturnPage
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.libs.json.Json
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents}
import renderer.Renderer
import uk.gov.hmrc.play.bootstrap.controller.FrontendBaseController
import uk.gov.hmrc.viewmodels.{NunjucksSupport, Radios}

import scala.concurrent.{ExecutionContext, Future}

class ConfirmSubmitAFTReturnController @Inject()(override val messagesApi: MessagesApi,
                                                 userAnswersCacheConnector: UserAnswersCacheConnector,
                                                 navigator: CompoundNavigator,
                                                 identify: IdentifierAction,
                                                 getData: DataRetrievalAction,
                                                 allowAccess: AllowAccessActionProvider,
                                                 allowSubmission: AllowSubmissionAction,
                                                 requireData: DataRequiredAction,
                                                 formProvider: ConfirmSubmitAFTReturnFormProvider,
                                                 val controllerComponents: MessagesControllerComponents,
                                                 config: FrontendAppConfig,
                                                 renderer: Renderer
                                                )(implicit ec: ExecutionContext) extends FrontendBaseController with I18nSupport with NunjucksSupport {

  private val form = formProvider()

  def onPageLoad(mode: Mode, srn: String): Action[AnyContent] = (identify andThen getData(srn) andThen
    allowAccess(srn) andThen allowSubmission andThen requireData).async {
    implicit request =>
      DataRetrievals.retrieveSchemeName { schemeName =>
        val preparedForm = request.userAnswers.get(ConfirmSubmitAFTReturnPage) match {
          case None => form
          case Some(value) => form.fill(value)
        }

        val viewModel = GenericViewModel(
          submitUrl = routes.ConfirmSubmitAFTReturnController.onSubmit(mode, srn).url,
          returnUrl = config.managePensionsSchemeSummaryUrl.format(srn),
          schemeName = schemeName)

        val json = Json.obj(
          fields = "srn" -> srn,
          "form" -> preparedForm,
          "viewModel" -> viewModel,
          "radios" -> Radios.yesNo(preparedForm("value"))
        )

        renderer.render(template = "confirmSubmitAFTReturn.njk", json).map(Ok(_))
      }
  }

  def onSubmit(mode: Mode, srn: String): Action[AnyContent] = (identify andThen getData(srn) andThen
    allowAccess(srn) andThen allowSubmission andThen requireData).async {
    implicit request =>
      DataRetrievals.retrieveSchemeName { schemeName =>
        form.bindFromRequest().fold(
          formWithErrors => {

            val viewModel = GenericViewModel(
              submitUrl = routes.ConfirmSubmitAFTReturnController.onSubmit(mode, srn).url,
              returnUrl = config.managePensionsSchemeSummaryUrl.format(srn),
              schemeName = schemeName)

            val json = Json.obj(
              fields = "srn" -> srn,
              "form" -> formWithErrors,
              "viewModel" -> viewModel,
              "radios" -> Radios.yesNo(formWithErrors("value"))
            )

            renderer.render(template = "confirmSubmitAFTReturn.njk", json).map(BadRequest(_))
          },
          value =>
            if (!value) {
              userAnswersCacheConnector.removeAll(request.internalId).map { _ =>
                Redirect(config.managePensionsSchemeSummaryUrl.format(srn))
              }
            } else {
              for {
                updatedAnswers <- Future.fromTry(request.userAnswers.set(ConfirmSubmitAFTReturnPage, value))
                _ <- userAnswersCacheConnector.save(request.internalId, updatedAnswers.data)
              } yield Redirect(navigator.nextPage(ConfirmSubmitAFTReturnPage, mode, updatedAnswers, srn))
            }
        )
      }
  }
}
