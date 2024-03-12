![GitHub release (latest by date)](https://img.shields.io/github/v/release/hmrc/trader-services-route-one-frontend) ![GitHub code size in bytes](https://img.shields.io/github/languages/code-size/hmrc/trader-services-route-one-frontend) ![GitHub last commit](https://img.shields.io/github/last-commit/hmrc/trader-services-route-one-frontend)

# trader-services-route-one-frontend

Frontend microservice exposing Trader Portal UI.

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

### License


This code is open source software licensed under the [Apache 2.0 License]("http://www.apache.org/licenses/LICENSE-2.0.html")
