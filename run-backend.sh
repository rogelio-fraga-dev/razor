#!/bin/bash
cd ~/barberbot-core
export $(grep -v '^#' .env | xargs)
./mvnw spring-boot:run 2>&1 | tee -a ~/barberbot-core/barberbot.log
