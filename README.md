# TweetAndTrip
Application to recommend trips based on Twitter profiles.

Select any of the following options after executing:

1 - Collect more data from Twitter (Database server must be running)*
2 - Evaluate top N
3 - Run API**
4 - Exit


*How to run MYSQL server daemon:

Start MySQL daemon with 'mysqld --console'
Connect HeidiSQL
Start our API

**API URL to test

http://localhost:4567/destination?name=NAME


Maven supported, to create a new SNAPSHOT version of this program just execute at root directory:

mvn compile
mvn package

And new .jar with dependencies will be generated.