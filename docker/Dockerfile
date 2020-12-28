FROM openjdk:8-jre-alpine
COPY app/HttpTester.jar app/HttpTester.jar
COPY app/HttpTester.properties app/HttpTester.properties
CMD ["java", "-cp", "app/HttpTester.jar:app/.", "Main.AppEntryPoint"]