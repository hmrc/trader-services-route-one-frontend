![GitHub release (latest by date)](https://img.shields.io/github/v/release/hmrc/trader-services-route-one-frontend) ![GitHub code size in bytes](https://img.shields.io/github/languages/code-size/hmrc/trader-services-route-one-frontend) ![GitHub last commit](https://img.shields.io/github/last-commit/hmrc/trader-services-route-one-frontend)

# trader-services-route-one-frontend

- Frontend microservice exposing Trader Portal UI.
- Traders, or their Agent need to complete Import/Export Declarations through CHIEF for goods imported or exported outside of the UK. Based on a set of rules, an import / export declaration may be subject to certain checks which are triggered when the goods enter or leave the country.
- These checks requires the trader to submit documents to be checked before goods can be released.
- This service allows Traders or their Agent to complete an online NCH1 form, attach the necessary documentation and to submit it the National Clearing Hub (NCH) for processing.
- Service covers: Route 1, Route 2, Route 3, Route 6.
- Users can upload: NCH1 form, C1601 form, C1602 form, C1603 form.

Features:
- Send documents for pre-clearance checks

## Running the tests

    sbt test it/test

## Running the tests with coverage

    sbt clean coverageOn test it/test coverageReport

## Running the app locally

    sm2 --start TRADER_SERVICES_ALL
    sm2 --stop TRADER_SERVICES_ROUTE_ONE_FRONTEND 
    sbt run

It should then be listening on port 9379

    browse http://localhost:9379/send-documents-for-customs-check

## Other related Route1 services:
- Backend service: [trader-services-route-one](https://github.com/hmrc/trader-services-route-one)
- Stubs: [trader-services-route-one-stub](https://github.com/hmrc/trader-services-route-one-stub/)


### License


This code is open source software licensed under the [Apache 2.0 License]("http://www.apache.org/licenses/LICENSE-2.0.html")
