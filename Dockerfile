FROM openjdk

WORKDIR /app

COPY build/libs/kuru-1.0.jar .

CMD java -jar kuru-1.0.jar