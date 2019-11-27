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

package navigators

import models.{Mode, UserAnswers}
import pages.Page
import play.api.mvc.Call
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.ExecutionContext

class FakeNavigator(desiredRoute: Call) extends Navigator {
  protected def routeMap(id: Page, userAnswers: UserAnswers): Option[Call] = Option(desiredRoute)

  protected def editRouteMap(id: Page, userAnswers: UserAnswers): Option[Call] = Option(desiredRoute)

  override def nextPageOptional(id: Page, mode: Mode, userAnswers: UserAnswers, srn: Option[String] = None)
                               (implicit ec: ExecutionContext, hc: HeaderCarrier): Option[Call] =
    Option(desiredRoute)
}
