
# Guardtime backend assignment
[A Swagger overview of the finished API.](https://app.swaggerhub.com/apis/Assigments/GuardTime/1.0.0) 

## Compatibility
In order to run the program, The Java Development Kit (JDK), version 1.8 or higher is needed and a A Gradle distribution, version 5.4.1 or higher.

## Dependancies
Apart from Guardtime's own, I used Gson to convert from and to json.

## Usage
Open with intellij or similar. Mind to add system properties for KSI signing. With intellij do it by going to VM options( Run | Edit Configurations). System properties needed are `-Dksi.login.key=[key] -Dksi.login.id=[username] -Daggregator.url=[signing-aggregation-endpoint-url]`.

Alternatively from terminal:
1. Navigate in terminal to /GT (directory where ./gradlew is located).
2. run command: `.gradlew/ build`
3. run command:  `./gradlew run -Dksi.login.key=[key] -Dksi.login.id=[username] -Daggregator.url=[signing-aggregation-endpoint-url]`
4. Use postman or curl to call the api. For example 
a) create a container, the curl command would be: `curl -X PUT \
  http://localhost:1234/create \
  -H 'Content-Type: application/json' \
  -d '{
  "name": "konteiner"
}'`
b) read it: `curl -X GET \
  http://localhost:1234/read \
  -H 'Content-Type: application/json' `
c) sign it: `curl -X POST \
  http://localhost:1234/sign \
  -H 'Content-Type: application/json' \
  -d '{
  "name": "konteiner",
  "userId": "John.Smith"
}'`
d) delete the signature from the container: `curl -X DELETE \
  'http://localhost:1234/delete?name=konteiner&userId=john.smith' `
