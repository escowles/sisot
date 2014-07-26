sisot (stuff i saw on twitter)
========

retrieves tweets from your timeline and posts them to wordpress

i was tired of trying to find something i had seen on twitter recently,
dealing with the inadequate twitter search options i could find.  so i tried
a couple of different strategies, but settled on fetching tweets and posting
them to a wordpress blog, which has a couple of advantages that are important
(to me at least):

1. first and foremost, it provides decent search functionality with minimal
   code.
2. also important, everything can easily run in a shared hosting account,
   so it can continue chugging along when my computer is off, when i'm
   traveling, etc.
3. wordpress has tons of options to handle formatting, making content private,
   etc.

dependencies
------------
* [wordpress](https://wordpress.org/download/)
* [jdk 1.7](http://www.oracle.com/technetwork/java/javase/downloads/jdk7-downloads-1880260.html)
* [ant](http://ant.apache.org/bindownload.cgi)

bundled libraries
--------------------
* [org.json](http://json.org/java/)
* [twitter4j 4.0.1](http://twitter4j.org/en/index.html)
* [wordpress-java 0.5.1](https://code.google.com/p/wordpress-java/)
* [xmlrpc-client 1.1.1](http://ws.apache.org/xmlrpc/)

setup
-----

1. clone repo

	``` sh
	$ git clone git@github.com:escowles/sisot.git
	```

2. setup `sisot/build.properties` with path to data directory

	```
	sisot.dir=/path/to/sisot
	```

3. build

	``` sh
	$ ant install
	```

4. create twitter access tokens (see https://dev.twitter.com/docs/auth/tokens-devtwittercom) and update sisot.conf with your api keys and wordpress credentials.

5. run

	``` sh
	$ /path/to/sisot/sisot.sh
	```

6. run periodically with crontab

	* you may need to create a script that sets java path and executes sisot.sh called (e.g.) `sisot-cron.sh`:

	``` sh
	#!/bin/sh
	export PATH=$HOME/java/bin:$PATH
	/path/to/sisot/sisot.sh
	```

	* edit crontab with `crontab -e` and add an entry to run `sisot-cron.sh` every 10 minutes:

	``` sh
	0,10,20,30,40,50 * * * * /path/to/sisot-cron.sh >> /path/to/sisot/sisot.log
	```
