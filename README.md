# Spring Travel with Customization

This is a fork of the pivotal spring-travel application with several modifications. A version of this application is running here:
http://newrelictravel.cfapps.io

# Customizations

## Custom Transaction Naming

* New Relic Agents have out-of-the-box rules for how transactions are named in the UI
* Sometimes these rules don’t work for your environment
* The Agent API lets you easily modify the Transaction Name
* This code needs to be in a place where it is called on every Transaction

```java
		NewRelic.setTransactionName("Web", "Find Hotels");
```

## Custom Tracers

* New Relic Agents have out-of-the-box support for certain technology frameworks
* Sometimes your framework is not supported, or your company wrote your own framework
* An XML extension can be used to add visibility for a specific class / method

```XML
<?xml version="1.0" encoding="UTF-8"?>
<extension xmlns="https://newrelic.com/docs/java/xsd/v1.0"
	xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
	xsi:schemaLocation="newrelic-extension extension.xsd " name="extension-example"
	version="1.0" enabled="true">
	<instrumentation>
		<pointcut transactionStartPoint="true"
			excludeFromTransactionTrace="false" ignoreTransaction="false">
			<className>org.springframework.samples.travel.services.JpaBookingService</className>
			<method>
				<name>slowThisDown</name>
			</method>
		</pointcut>
	</instrumentation>
</extension>
```

## Custom Attributes

* New Relic Agents collect standard attributes (also called parameters) for every Transaction
* You may have special attributes unique to your application that you want to be collected as well
* Attributes show up in Transaction Traces as well as Insights Events
* The Agent API lets you easily add Custom Attributes
* This code needs to be in a place where it is called on every Transaction

```java 
		String pattern = getSearchPattern(criteria);
		log.debug("search pattern: " + pattern);
		NewRelic.addCustomParameter("searchCriteria", pattern);
```

## Custom Events

* You may want to record some kind of rich event that occurs on an irregular basis
* Through the Agent API, these Custom Events can be added as a new Event Type in Insights
* Create a Map with Key / Value pairs
* Publish event with the API

```java
		HashMap<String,Object> bookingMap = new HashMap<String,Object>();
		bookingMap.put("BookingNumberOfNights",booking.getNights() );
		bookingMap.put("BookingRevenue",Integer.valueOf(booking.getTotal().intValue()) );
		bookingMap.put("BookingRate",Integer.valueOf(booking.getHotel().getPrice().intValue() ) );
		bookingMap.put("BookingHotelName",booking.getHotel().getName() +" - " + booking.getHotel().getCity() + ", " + booking.getHotel().getState() );
		bookingMap.put("BookingCity", booking.getHotel().getCity() + ", " + booking.getHotel().getState() );
		bookingMap.put("BookingState", booking.getHotel().getState() );
		bookingMap.put("BookingZip", booking.getHotel().getZip() );
		bookingMap.put("BookingSmokingRoom",(booking.isSmoking()) ? "Smoking" : "Non-Smoking" );
		bookingMap.put("BookingNumberOfBeds",booking.getBeds() );
		bookingMap.put("BookingAgent",booking.getUser().getName() );	
		NewRelic.getAgent().getInsights().recordCustomEvent("Booking Event", bookingMap);
```

## Custom Metrics

* New Relic Agents have out-of-the-box support for standard non-transactional metrics
* Sometimes you have a special metric you want to report
* These metrics can be displayed in Custom Dashboards, or used in Alert Policies
* Add a call to send the numeric metric with a name like Custom/Metric Name

```java
		NewRelic.recordMetric("Custom/Booking Revenue", Integer.valueOf(booking.getTotal().intValue()) );
```
