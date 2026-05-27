#!/bin/bash
cd ~/barberbot-core
export $(grep -v '^#' .env | xargs)
java -jar target/barberbot-assist-1.0.0.jar > screen.log 2>&1
