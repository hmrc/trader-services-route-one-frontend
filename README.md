# Trader Services Frontend

Frontend microservice exposing Trader Portal UI.

Features:
- Route1 (NCH1) digital form with documents upload

## Running the tests

    sbt test it:test

## Running the tests with coverage

    sbt clean coverageOn test it:test coverageReport

## Running the app locally

    sm --start TRADER_SERVICES_ALL
    sm --stop TRADER_SERVICES_FRONTEND 
    sbt run

It should then be listening on port 9379

    browse http://localhost:9379/trader-services

### License


This code is open source software licensed under the [Apache 2.0 License]("http://www.apache.org/licenses/LICENSE-2.0.html")
