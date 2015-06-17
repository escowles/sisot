#!/bin/sh

# indexer - index tweets in solr

SISOT=`dirname $0`

CONF=$SISOT/sisot.conf

CP=""
for i in $SISOT/lib/*.jar $SISOT/lib; do
	CP=$i:$CP
done

echo `date +%T` Indexer
java -cp $CP org.ticklefish.sisot.Indexer $CONF
echo `date +%T` done
