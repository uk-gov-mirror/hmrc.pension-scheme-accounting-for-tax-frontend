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

import connectors.SchemeDetailsConnector
import connectors.cache.UserAnswersCacheConnector
import controllers.actions._
import javax.inject.Inject
import models.{NormalMode, UserAnswers}
import navigators.CompoundNavigator
import pages.SchemeNameQuery
import pages.chargeF.WhatYouWillNeedPage
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.libs.json.{JsSuccess, Json}
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents}
import renderer.Renderer
import uk.gov.hmrc.play.bootstrap.controller.FrontendBaseController

import scala.concurrent.{ExecutionContext, Future}

class whatYouWillNeedController @Inject()(
                                           override val messagesApi: MessagesApi,
                                           identify: IdentifierAction,
                                           getData: DataRetrievalAction,
                                           requireData: DataRequiredAction,
                                           val controllerComponents: MessagesControllerComponents,
                                           renderer: Renderer,
                                           schemeDetailsConnector: SchemeDetailsConnector,
                                           userAnswersCacheConnector: UserAnswersCacheConnector,
                                           navigator: CompoundNavigator
                                         )(implicit ec: ExecutionContext) extends FrontendBaseController with I18nSupport {

  def onPageLoad(srn: String): Action[AnyContent] = (identify andThen getData).async {
    implicit request =>
      val ua = request.userAnswers.getOrElse(UserAnswers())
      schemeDetailsConnector.getSchemeName(request.psaId.id, "srn", srn).flatMap { schemeName =>
        Future.fromTry(ua.set(SchemeNameQuery, schemeName)).flatMap { answers =>
          userAnswersCacheConnector.save(request.internalId, answers.data)//.flatMap { _ =>
            val nextPage = navigator.nextPage(WhatYouWillNeedPage, NormalMode, ua, srn)
            renderer.render(template = "chargeF/whatYouWillNeed.njk",
              Json.obj("schemeName" -> schemeName, "nextPage" -> nextPage.url)).map(Ok(_))
//          }
        }
      }
  }
}
