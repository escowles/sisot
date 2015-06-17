#!/bin/sh

# sisot - download tweets in a user timeline and convert them to wordpress posts

SISOT=`dirname $0`

CONF=$SISOT/sisot.conf

CP=""
for i in $SISOT/lib/*.jar $SISOT/lib; do
	CP=$i:$CP
done

for i in Lister Fetcher Indexer; do
	echo `date +%T` $i
	java -cp $CP org.ticklefish.sisot.$i $CONF
done
echo `date +%T` done
