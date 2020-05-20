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

package data

import java.time.LocalDate
import java.time.format.DateTimeFormatter

import models.SponsoringEmployerType.SponsoringEmployerTypeIndividual
import models.SponsoringEmployerType.SponsoringEmployerTypeOrganisation
import models.chargeB.ChargeBDetails
import models.chargeC.ChargeCDetails
import models.chargeC.SponsoringEmployerAddress
import models.chargeC.SponsoringOrganisationDetails
import models.chargeD.ChargeDDetails
import models.chargeE.ChargeEDetails
import models.chargeG.ChargeAmounts
import models.chargeG.{MemberDetails => MemberDetailsG}
import models.AccessMode
import models.SessionAccessData
import models.SessionData
import models.AFTOverview
import models.DisplayQuarter
import models.InProgressHint
import models.LockedHint
import models.MemberDetails
import models.Quarter
import models.SchemeDetails
import models.SchemeStatus
import models.SubmittedHint
import models.UserAnswers
import pages.chargeC.ChargeCDetailsPage
import pages.chargeC.SponsoringIndividualDetailsPage
import pages.chargeC.SponsoringOrganisationDetailsPage
import pages.chargeC.TotalChargeAmountPage
import pages.chargeC.WhichTypeOfSponsoringEmployerPage
import pages.chargeD.{ChargeDetailsPage => ChargeDDetailsPage}
import pages.chargeD.{MemberDetailsPage => ChargeDMemberDetailsPAge}
import pages.chargeE.ChargeDetailsPage
import pages.chargeE.MemberDetailsPage
import play.api.libs.json.Json
import play.api.mvc.Call
import utils.AFTConstants._

object SampleData {
  //scalastyle.off: magic.number
  val userAnswersId = "id"
  val psaId = "A0000000"
  val srn = "aa"
  val startDate = QUARTER_START_DATE
  val pstr = "pstr"
  val schemeName = "Big Scheme"
  val companyName = "Big Company"
  val crn = "AB121212"
  val dummyCall: Call = Call("GET", "/foo")
  val chargeAmount1 = BigDecimal(33.44)
  val chargeAmount2 = BigDecimal(50.00)
  val chargeAmounts = ChargeAmounts(chargeAmount1, chargeAmount2)
  val chargeAmounts2 = ChargeAmounts(chargeAmount1, chargeAmount2)
  val chargeFChargeDetails = models.chargeF.ChargeDetails(LocalDate.of(2020, 4, 3), BigDecimal(33.44))
  val chargeAChargeDetails = models.chargeA.ChargeDetails(44, Some(chargeAmount1), Some(BigDecimal(34.34)), BigDecimal(67.78))
  val chargeEDetails = ChargeEDetails(chargeAmount1, LocalDate.of(2019, 4, 3), isPaymentMandatory = true)
  val chargeCDetails = ChargeCDetails(paymentDate = QUARTER_START_DATE,amountTaxDue = chargeAmount1)
  val chargeDDetails = ChargeDDetails(QUARTER_START_DATE, Option(chargeAmount1), Option(chargeAmount2))
  val chargeGDetails = models.chargeG.ChargeDetails(qropsReferenceNumber = "123456", qropsTransferDate = QUARTER_START_DATE)
  val schemeDetails: SchemeDetails = SchemeDetails(schemeName, pstr, SchemeStatus.Open.toString)
  val version = "1"

  val sponsoringOrganisationDetails: SponsoringOrganisationDetails =
    SponsoringOrganisationDetails(name = companyName, crn = crn)
  val sponsoringIndividualDetails: MemberDetails =
    MemberDetails(firstName = "First", lastName = "Last", nino = "CS121212C")

  val sponsoringIndividualDetailsDeleted: MemberDetails =
    MemberDetails(firstName = "First", lastName = "Last", nino = "CS121212C", isDeleted = true)
  val sponsoringOrganisationDetailsDeleted: SponsoringOrganisationDetails =
    SponsoringOrganisationDetails(name = companyName, crn = crn, isDeleted = true)

  val sponsoringEmployerAddress: SponsoringEmployerAddress =
    SponsoringEmployerAddress(
      line1 = "line1",
      line2 = "line2",
      line3 = Some("line3"),
      line4 = Some("line4"),
      country = "GB",
      postcode = Some("ZZ1 1ZZ")
    )

  val sessionId = "1234567890"
  val lockedByName = Some("Name")
  val accessModeViewOnly = AccessMode.PageAccessModeViewOnly

  def sessionAccessData(version: Int = version.toInt, accessMode: AccessMode = AccessMode.PageAccessModeCompile) =
    SessionAccessData(version, accessMode)

  val sessionAccessDataCompile = SessionAccessData(version.toInt, AccessMode.PageAccessModeCompile)
  val sessionAccessDataPreCompile = SessionAccessData(version.toInt, AccessMode.PageAccessModePreCompile)

  def sessionData(
                   sessionId: String = sessionId,
                   name: Option[String]= lockedByName,
                   sessionAccessData: SessionAccessData = sessionAccessDataCompile
                 ) =
    SessionData(sessionId, lockedByName, sessionAccessData)

  def userAnswersWithSchemeName: UserAnswers =
    UserAnswers(Json.obj(
      "schemeName" -> schemeName,
      "pstr" -> pstr)
    )

  def userAnswersWithSchemeNamePstrQuarter: UserAnswers =
    UserAnswers(Json.obj(
      "schemeName" -> schemeName,
      "pstr" -> pstr,
      "quarter" -> Quarter(QUARTER_START_DATE, QUARTER_END_DATE))
    )

  def userAnswersWithSchemeNameAndOrganisation: UserAnswers = userAnswersWithSchemeNamePstrQuarter
    .set(SponsoringOrganisationDetailsPage(0), sponsoringOrganisationDetails).toOption.get
    .set(WhichTypeOfSponsoringEmployerPage(0), SponsoringEmployerTypeOrganisation).toOption.get

  def userAnswersWithSchemeNameAndIndividual: UserAnswers = userAnswersWithSchemeNamePstrQuarter
    .set(SponsoringIndividualDetailsPage(0), sponsoringIndividualDetails).toOption.get
    .set(WhichTypeOfSponsoringEmployerPage(0), SponsoringEmployerTypeIndividual).toOption.get


  val chargeBDetails: ChargeBDetails = ChargeBDetails(4, chargeAmount1)
  val memberDetails: MemberDetails = MemberDetails("first", "last", "AB123456C")
  val memberDetails2: MemberDetails = MemberDetails("Joe", "Bloggs", "AB123456C")
  val memberGDetails: MemberDetailsG = MemberDetailsG("first", "last", LocalDate.now(), "AB123456C")
  val memberGDetails2: MemberDetailsG = MemberDetailsG("Joe", "Bloggs", LocalDate.now(), "AB123456C")
  val memberDetailsDeleted: MemberDetails = MemberDetails("Jill", "Bloggs", "AB123456C", isDeleted = true)
  val memberGDetailsDeleted: MemberDetailsG = MemberDetailsG("Jill", "Bloggs", LocalDate.now(), "AB123456C", isDeleted = true)

  val chargeCEmployer: UserAnswers = userAnswersWithSchemeNameAndIndividual
    .setOrException(ChargeCDetailsPage(0), chargeCDetails)
    .setOrException(TotalChargeAmountPage, chargeAmount1)

  val chargeEMember: UserAnswers = userAnswersWithSchemeNamePstrQuarter
    .set(MemberDetailsPage(0), memberDetails).toOption.get
    .set(ChargeDetailsPage(0), chargeEDetails).toOption.get
    .set(pages.chargeE.TotalChargeAmountPage, chargeAmount1).toOption.get

  val chargeGMember: UserAnswers = userAnswersWithSchemeNamePstrQuarter
    .set(pages.chargeG.MemberDetailsPage(0), memberGDetails).toOption.get
    .set(pages.chargeG.ChargeDetailsPage(0), chargeGDetails).toOption.get
    .set(pages.chargeG.ChargeAmountsPage(0), chargeAmounts).toOption.get
    .set(pages.chargeG.TotalChargeAmountPage, BigDecimal(83.44)).toOption.get

  val chargeDMember: UserAnswers = userAnswersWithSchemeNamePstrQuarter
    .set(ChargeDMemberDetailsPAge(0), memberDetails).toOption.get
    .set(ChargeDDetailsPage(0), chargeDDetails).toOption.get
    .set(pages.chargeD.TotalChargeAmountPage, BigDecimal(83.44)).toOption.get

  val dateFormatter = DateTimeFormatter.ofPattern("d MMMM yyyy")

  val overview1: AFTOverview =
    AFTOverview(
      periodStartDate = LocalDate.of(2020,4,1),
      periodEndDate = LocalDate.of(2028,6,30),
      numberOfVersions = 2,
      submittedVersionAvailable = false,
      compiledVersionAvailable = true
    )

  val overview2: AFTOverview =
    AFTOverview(
      periodStartDate = LocalDate.of(2020,10,1),
      periodEndDate = LocalDate.of(2020,12,31),
      numberOfVersions = 3,
      submittedVersionAvailable = false,
      compiledVersionAvailable = true
    )

  val overview3: AFTOverview =
    AFTOverview(
      periodStartDate = LocalDate.of(2022,1,1),
      periodEndDate = LocalDate.of(2022,3,31),
      numberOfVersions = 1,
      submittedVersionAvailable = true,
      compiledVersionAvailable = false
    )

  val q22020: Quarter = Quarter(LocalDate.of(2020, 4, 1), LocalDate.of(2020, 6, 30))
  val q32020: Quarter = Quarter(LocalDate.of(2020, 7, 1), LocalDate.of(2020, 9, 30))
  val q42020: Quarter = Quarter(LocalDate.of(2020, 10, 1), LocalDate.of(2020, 12, 31))
  val q12021: Quarter = Quarter(LocalDate.of(2021, 1, 1), LocalDate.of(2021, 3, 31))

  val displayQuarterLocked: DisplayQuarter = DisplayQuarter(q32020, displayYear = false, Some(psaId), Some(LockedHint))
  val displayQuarterContinueAmend: DisplayQuarter = DisplayQuarter(q42020, displayYear = true, None, Some(InProgressHint))
  val displayQuarterViewPast: DisplayQuarter = DisplayQuarter(q22020, displayYear = false, None, Some(SubmittedHint))
  val displayQuarterStart: DisplayQuarter = DisplayQuarter(q12021, displayYear = false, None, None)

  val aftOverviewQ22020: AFTOverview =
    AFTOverview(q22020.startDate, q22020.endDate, numberOfVersions = 1, submittedVersionAvailable = true, compiledVersionAvailable = false)
  val aftOverviewQ32020: AFTOverview =
    AFTOverview(q32020.startDate, q32020.endDate, numberOfVersions = 1, submittedVersionAvailable = true, compiledVersionAvailable = true)
  val aftOverviewQ42020: AFTOverview =
    AFTOverview(q42020.startDate, q42020.endDate, numberOfVersions = 1, submittedVersionAvailable = true, compiledVersionAvailable = false)
  val aftOverviewQ12021: AFTOverview =
    AFTOverview(q12021.startDate, q12021.endDate, numberOfVersions = 1, submittedVersionAvailable = true, compiledVersionAvailable = false)

}
