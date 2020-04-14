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

package controllers.chargeC

import connectors.AddressLookupConnector
import controllers.actions.MutableFakeDataRetrievalAction
import controllers.base.ControllerSpecBase
import data.SampleData.dummyCall
import data.SampleData.srn
import data.SampleData.startDate
import data.SampleData.userAnswersWithSchemeNameAndIndividual
import matchers.JsonMatchers
import models.GenericViewModel
import models.NormalMode
import models.TolerantAddress
import models.UserAnswers
import org.mockito.ArgumentCaptor
import org.mockito.Matchers
import org.mockito.Matchers.any
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.Mockito.when
import org.scalatest.OptionValues
import org.scalatest.TryValues
import org.scalatestplus.mockito.MockitoSugar
import pages.chargeC.AddressListPage
import pages.chargeC.SponsoringOrganisationDetailsPage
import pages.chargeC.WhichTypeOfSponsoringEmployerPage
import play.api.Application
import play.api.data.Form
import play.api.libs.json.JsObject
import play.api.libs.json.Json
import play.api.test.Helpers._
import play.twirl.api.Html
import uk.gov.hmrc.viewmodels.NunjucksSupport
import models.LocalDateBinder._
import data.SampleData._
import forms.chargeC.AddressListFormProvider
import pages.chargeC.EnterPostcodePage
import pages.chargeC.SponsoringEmployerAddressPage
import pages.chargeC.SponsoringIndividualDetailsPage

import scala.concurrent.Future

class AddressListControllerSpec extends ControllerSpecBase with MockitoSugar with NunjucksSupport with JsonMatchers with OptionValues with TryValues {

  private val mockAddressLookupConnector = mock[AddressLookupConnector]
  private val mutableFakeDataRetrievalAction: MutableFakeDataRetrievalAction = new MutableFakeDataRetrievalAction()
  private val application: Application =
    applicationBuilderMutableRetrievalAction(mutableFakeDataRetrievalAction).build()
  private val templateToBeRendered = "chargeC/addressList.njk"
  private val form = new AddressListFormProvider()()
  private val index = 0
  private val postcode = "ZZ1 1ZZ"
  private val seqAddresses =
    Seq[TolerantAddress](
      TolerantAddress(
        Some(sponsoringEmployerAddress.line1),
        Some(sponsoringEmployerAddress.line2),
        sponsoringEmployerAddress.line3,
        sponsoringEmployerAddress.line4,
        sponsoringEmployerAddress.postcode,
        Some(sponsoringEmployerAddress.country)
      )
    )

  private val userAnswersIndividual: Option[UserAnswers] = Some(
    userAnswersWithSchemeNameAndIndividual.setOrException(EnterPostcodePage, seqAddresses)
  )

  private def httpPathGET: String = controllers.chargeC.routes.AddressListController.onPageLoad(NormalMode, srn, startDate, index).url

  private def httpPathPOST: String = controllers.chargeC.routes.AddressListController.onSubmit(NormalMode, srn, startDate, index).url

  private val valuesValid: Map[String, Seq[String]] = Map(
    "value" -> Seq("0")
  )

  private val valuesInvalid: Map[String, Seq[String]] = Map(
    "value" -> Seq("")
  )

  private def transformAddressesForTemplate(seqTolerantAddresses: Seq[TolerantAddress]): Seq[JsObject] = {
    for ((row, i) <- seqTolerantAddresses.zipWithIndex) yield {
      Json.obj(
        "value" -> i,
        "text" -> row.print
      )
    }
  }

  private def jsonToPassToTemplate(sponsorName: String, isSelected: Boolean = false): Form[Int] => JsObject =
    form =>
      Json.obj(
        "form" -> form,
        "viewModel" -> GenericViewModel(
          submitUrl = controllers.chargeC.routes.AddressListController.onSubmit(NormalMode, srn, startDate, index).url,
          returnUrl = dummyCall.url,
          schemeName = schemeName
        ),
        "sponsorName" -> sponsorName,
        "enterManuallyUrl" -> routes.SponsoringEmployerAddressController.onPageLoad(NormalMode, srn, startDate, index).url,
        "addresses" -> transformAddressesForTemplate(seqAddresses)
    )

  override def beforeEach: Unit = {
    super.beforeEach
    when(mockUserAnswersCacheConnector.save(any(), any())(any(), any())).thenReturn(Future.successful(Json.obj()))
    when(mockRenderer.render(any(), any())(any())).thenReturn(Future.successful(Html("")))
    when(mockAppConfig.managePensionsSchemeSummaryUrl).thenReturn(dummyCall.url)
    when(mockAppConfig.validCountryCodes).thenReturn(Seq("UK"))
  }

  "AddressList Controller with individual sponsor" must {
    "return OK and the correct view for a GET" in {
      mutableFakeDataRetrievalAction.setDataToReturn(userAnswersIndividual)
      val templateCaptor = ArgumentCaptor.forClass(classOf[String])
      val jsonCaptor = ArgumentCaptor.forClass(classOf[JsObject])

      val result = route(application, httpGETRequest(httpPathGET)).value

      status(result) mustEqual OK

      verify(mockRenderer, times(1)).render(templateCaptor.capture(), jsonCaptor.capture())(any())

      templateCaptor.getValue mustEqual templateToBeRendered

      val expectedJson = jsonToPassToTemplate(sponsorName = s"${sponsoringIndividualDetails.firstName} ${sponsoringIndividualDetails.lastName}")
        .apply(form)

      jsonCaptor.getValue must containJson(expectedJson)
    }

    "redirect to Session Expired page for a GET when there is no data" in {
      mutableFakeDataRetrievalAction.setDataToReturn(None)

      val result = route(application, httpGETRequest(httpPathGET)).value

      status(result) mustEqual SEE_OTHER

      redirectLocation(result).value mustBe controllers.routes.SessionExpiredController.onPageLoad().url
    }

    "Save data to user answers and redirect to next page when valid data is submitted" in {
      val expectedJson = Json.obj(
        "chargeCDetails" -> Json.obj(
          "employers" -> Json.arr(
            Json.obj(
              SponsoringIndividualDetailsPage.toString -> sponsoringIndividualDetails,
              WhichTypeOfSponsoringEmployerPage.toString -> "individual",
              SponsoringEmployerAddressPage.toString -> sponsoringEmployerAddress
            ))
        ),

        EnterPostcodePage.toString -> seqAddresses
      )

      when(mockCompoundNavigator.nextPage(Matchers.eq(AddressListPage), any(), any(), any(), any())).thenReturn(dummyCall)
      when(mockAddressLookupConnector.addressLookupByPostCode(any())(any(), any())).thenReturn(Future.successful(seqAddresses))

      mutableFakeDataRetrievalAction.setDataToReturn(userAnswersIndividual)

      val jsonCaptor = ArgumentCaptor.forClass(classOf[JsObject])

      val result = route(application, httpPOSTRequest(httpPathPOST, valuesValid)).value

      status(result) mustEqual SEE_OTHER

      verify(mockUserAnswersCacheConnector, times(1)).save(any(), jsonCaptor.capture)(any(), any())

      jsonCaptor.getValue must containJson(expectedJson)

      redirectLocation(result) mustBe Some(dummyCall.url)
    }

    "return a BAD REQUEST when invalid data is submitted" in {
      mutableFakeDataRetrievalAction.setDataToReturn(userAnswersIndividual)

      val result = route(application, httpPOSTRequest(httpPathPOST, valuesInvalid)).value

      status(result) mustEqual BAD_REQUEST

      verify(mockUserAnswersCacheConnector, times(0)).save(any(), any())(any(), any())
    }

    "redirect to Session Expired page for a POST when there is no data" in {
      mutableFakeDataRetrievalAction.setDataToReturn(None)

      val result = route(application, httpPOSTRequest(httpPathPOST, valuesValid)).value

      status(result) mustEqual SEE_OTHER

      redirectLocation(result).value mustBe controllers.routes.SessionExpiredController.onPageLoad().url
    }
  }
}
