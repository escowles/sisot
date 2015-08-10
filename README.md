sisot (stuff i saw on twitter)
========

retrieves tweets from your timeline and indexes them in solr.

i was tired of trying to find something i had seen on twitter recently,
dealing with the inadequate twitter search options i could find.  so i tried
a couple of different strategies, but settled on fetching tweets and indexing
them in solr (using a [blacklight app](https://github.com/escowles/sob/) to
search them).

dependencies
------------
* [solr](https://lucnee.apache.org/solr/)
* [jdk 1.7](http://www.oracle.com/technetwork/java/javase/downloads/jdk7-downloads-1880260.html)
* [ant](http://ant.apache.org/bindownload.cgi)

bundled libraries
--------------------
* [org.json](http://json.org/java/)
* [twitter4j 4.0.1](http://twitter4j.org/en/index.html)

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
