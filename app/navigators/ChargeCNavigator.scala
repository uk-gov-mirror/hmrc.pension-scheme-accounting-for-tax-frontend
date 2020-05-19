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

package navigators

import java.time.LocalDate

import com.google.inject.Inject
import config.FrontendAppConfig
import connectors.cache.UserAnswersCacheConnector
import controllers.chargeC.routes._
import helpers.ChargeCHelper._
import models.LocalDateBinder._
import models.SponsoringEmployerType._
import models.requests.DataRequest
import models.{CheckMode, NormalMode, SponsoringEmployerType, UserAnswers}
import pages.Page
import pages.chargeC.{SponsoringEmployerAddressSearchPage, _}
import play.api.mvc.{AnyContent, Call}
import utils.DeleteChargeHelper

class ChargeCNavigator @Inject()(val dataCacheConnector: UserAnswersCacheConnector,
                                 deleteChargeHelper: DeleteChargeHelper,
                                 config: FrontendAppConfig)
  extends Navigator {

  def nextIndex(ua: UserAnswers, srn: String, startDate: LocalDate)(implicit request: DataRequest[AnyContent]): Int =
    getSponsoringEmployersIncludingDeleted(ua, srn, startDate).size

  def addEmployers(ua: UserAnswers, srn: String, startDate: LocalDate)(implicit request: DataRequest[AnyContent]): Call = ua.get(AddEmployersPage) match {
    case Some(true) => WhichTypeOfSponsoringEmployerController.onPageLoad(NormalMode, srn, startDate, nextIndex(ua, srn, startDate))
    case _          => controllers.routes.AFTSummaryController.onPageLoad(srn, startDate, None)
  }

  //scalastyle:off cyclomatic.complexity
  override protected def routeMap(ua: UserAnswers, srn: String, startDate: LocalDate)
                                 (implicit request: DataRequest[AnyContent]): PartialFunction[Page, Call] = {
    case WhatYouWillNeedPage =>
      WhichTypeOfSponsoringEmployerController.onPageLoad(NormalMode, srn, startDate, nextIndex(ua, srn, startDate))

    case WhichTypeOfSponsoringEmployerPage(index) if ua.get(WhichTypeOfSponsoringEmployerPage(index)).contains(SponsoringEmployerTypeOrganisation) =>
      SponsoringOrganisationDetailsController.onPageLoad(NormalMode, srn, startDate, index)

    case WhichTypeOfSponsoringEmployerPage(index) if ua.get(WhichTypeOfSponsoringEmployerPage(index)).contains(SponsoringEmployerTypeIndividual) =>
      SponsoringIndividualDetailsController.onPageLoad(NormalMode, srn, startDate, index)

    case SponsoringOrganisationDetailsPage(index) =>
      SponsoringEmployerAddressSearchController.onPageLoad(NormalMode, srn, startDate, index)

    case SponsoringIndividualDetailsPage(index) =>
      SponsoringEmployerAddressSearchController.onPageLoad(NormalMode, srn, startDate, index)

    case SponsoringEmployerAddressSearchPage(index) =>
      SponsoringEmployerAddressResultsController.onPageLoad(NormalMode, srn, startDate, index)

    case SponsoringEmployerAddressResultsPage(index) =>
      ChargeDetailsController.onPageLoad(NormalMode, srn, startDate, index)

    case SponsoringEmployerAddressPage(index) =>
      ChargeDetailsController.onPageLoad(NormalMode, srn, startDate, index)

    case ChargeCDetailsPage(index) =>
      CheckYourAnswersController.onPageLoad(srn, startDate, index)

    case CheckYourAnswersPage =>
      AddEmployersController.onPageLoad(srn, startDate)

    case AddEmployersPage =>
      addEmployers(ua, srn, startDate)

    case DeleteEmployerPage if getSponsoringEmployers(ua, srn, startDate).nonEmpty =>
      AddEmployersController.onPageLoad(srn, startDate)

    case DeleteEmployerPage if deleteChargeHelper.hasLastChargeOnly(ua) =>
      Call("GET", config.managePensionsSchemeSummaryUrl.format(srn))

    case DeleteEmployerPage =>
      controllers.routes.AFTSummaryController.onPageLoad(srn, startDate, None)
  }

  //scalastyle:on cyclomatic.complexity

  override protected def editRouteMap(ua: UserAnswers, srn: String, startDate: LocalDate)
                                     (implicit request: DataRequest[AnyContent]): PartialFunction[Page, Call] = {
    case WhichTypeOfSponsoringEmployerPage(index) if ua.get(WhichTypeOfSponsoringEmployerPage(index)).contains(SponsoringEmployerTypeOrganisation) =>
      SponsoringOrganisationDetailsController.onPageLoad(CheckMode, srn, startDate, index)

    case WhichTypeOfSponsoringEmployerPage(index) if ua.get(WhichTypeOfSponsoringEmployerPage(index)).contains(SponsoringEmployerTypeIndividual) =>
      SponsoringIndividualDetailsController.onPageLoad(CheckMode, srn, startDate, index)

    case SponsoringOrganisationDetailsPage(index) =>
      editRoutesForSponsoringEmployerPages(index, ua, srn, startDate)

    case SponsoringIndividualDetailsPage(index) =>
      editRoutesForSponsoringEmployerPages(index, ua, srn, startDate)

    case SponsoringEmployerAddressPage(index) =>
      editRoutesForSponsoringEmployerAddress(index, ua, srn, startDate)

    case ChargeCDetailsPage(index) =>
      CheckYourAnswersController.onPageLoad(srn, startDate, index)
  }

  private def editRoutesForSponsoringEmployerPages(index: Int, ua: UserAnswers, srn: String, startDate: LocalDate): Call = {
    ua.get(SponsoringEmployerAddressPage(index)) match {
      case Some(_) => CheckYourAnswersController.onPageLoad(srn, startDate, index)
      case _       => SponsoringEmployerAddressController.onPageLoad(CheckMode, srn, startDate, index)
    }
  }

  private def editRoutesForSponsoringEmployerAddress(index: Int, ua: UserAnswers, srn: String, startDate: LocalDate): Call = {
    ua.get(ChargeCDetailsPage(index)) match {
      case Some(_) => CheckYourAnswersController.onPageLoad(srn, startDate, index)
      case _       => ChargeDetailsController.onPageLoad(CheckMode, srn, startDate, index)
    }
  }

}
