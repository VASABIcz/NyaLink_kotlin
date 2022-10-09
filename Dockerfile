FROM openjdk

WORKDIR /app

COPY build/libs/kuru-1.0.jar .

CMD java -jar newBot-1.0.jar