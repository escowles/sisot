sisot (stuff i saw on twitter)
========

* retrieves tweets from your timeline and posts them to wordpress

bundled dependencies
--------------------
* [org.json](http://json.org/java/)
* [twitter4j 4.0.1](http://twitter4j.org/en/index.html)
* [wordpress-java 0.5.1](https://code.google.com/p/wordpress-java/)
* [xmlrpc-client 1.1.1](http://ws.apache.org/xmlrpc/)

dependencies
------------
* [wordpress](https://wordpress.org/download/)
* [jdk 1.7](http://www.oracle.com/technetwork/java/javase/downloads/jdk7-downloads-1880260.html)
* [ant](http://ant.apache.org/bindownload.cgi)

setup
-----

1. clone repo

	``` sh
	$ git clone git@github.com:escowles/sisot.git
	```

2. setup sisot/build.properties with path to data directory

	```
	sisot=/path/to/sisot
	```

3. build

	``` sh
	$ ant install
	```

4. run

	``` sh
	$ /path/to/sisot/sisot.sh
	```

5. crontab...

	* sisot-cron.sh:

	``` sh
	#!/bin/sh
	export PATH=$HOME/java/bin:$PATH
	$HOME/export/sisot/sisot.sh
	```

	* crontab -e:

	``` sh
	0,10,20,30,40,50 * * * * /path/to/sisot-cron.sh >> /path/to/sisot/sisot.log
	```
