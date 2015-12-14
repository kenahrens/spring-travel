package org.springframework.samples.travel.services;
 
import com.newrelic.api.agent.NewRelic;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.samples.travel.domain.Booking;
import org.springframework.samples.travel.domain.Hotel;
import org.springframework.samples.travel.domain.User;
import org.springframework.stereotype.Repository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import javax.persistence.TypedQuery;
import javax.persistence.criteria.*;

import java.util.*;
import java.net.*;

/**
 * A JPA-based implementation of the Booking Service. Delegates to a JPA entity
 * manager to issue data access calls against the backing repository. The
 * EntityManager reference is provided by the managing container (Spring)
 * automatically.
 */
@Service("bookingService")
@Repository
public class JpaBookingService implements BookingService {

	private Log log = LogFactory.getLog(getClass());
  
	private EntityManager em;

	@PersistenceContext
	public void setEntityManager(EntityManager em) {
		this.em = em;
	}

	@Transactional(readOnly = true)
	@SuppressWarnings("unchecked")
	public List<Booking> findBookings(String username) {
		if (username != null) {
			return em.createQuery("select b from Booking b where b.user.username = :username order by b.checkinDate").setParameter("username", username).getResultList();
		} else {
			return null;
		}
	}

	@Transactional(readOnly = true)
	public List<Hotel> findHotels(SearchCriteria criteria) {

		NewRelic.setTransactionName("Web", "Find Hotels");
		
		String pattern = getSearchPattern(criteria);
		log.debug("search pattern: " + pattern);
		NewRelic.addCustomParameter("searchCriteria",pattern ); 

		
		//     If the user enters only a single character in the search box, let's throw an unhandled exception.

		if(criteria.getSearchString().length() == 1) {
			throwAnException("Now why'd you go and search for just 1 character?  That's just gonna throw an Exception!");
			return null;
		}
		
		//    **************************************************************
		//    Adding a Call to RabbitMQ to put the searchCriteria on a queue
		//    **************************************************************
		try{
			String Data = "value=" + pattern;
		    URL url = new URL("http://nr-abbitmq-spring.cfapps.io/publish");
		    HttpURLConnection con = (HttpURLConnection) url.openConnection();
		    con.setRequestMethod("POST");
		    con.setDoOutput(true);
		    con.getOutputStream().write(Data.getBytes("UTF-8"));
		    con.getInputStream();
		    con.disconnect();
		} catch(Exception ex) {
			 
		}
	    
		
		
		
	/*	if(Http.post("http://nr-abbitmq-spring.cfapps.io/publish", pattern).text().equals("OK")){
		    //..success
		}else{
		    //failure
		}
	*/
		//    **************************************************************

		
		slowThisDown(criteria.getSlowDown()*1000);
		
		CriteriaBuilder criteriaBuilder = em.getCriteriaBuilder();

		CriteriaQuery<Hotel> hotelCriteriaQuery = criteriaBuilder.createQuery(Hotel.class);

		Root<Hotel> from = hotelCriteriaQuery.from(Hotel.class);

		Expression<String> city = from.get("city");
		Expression<String> zip = from.get("zip");
		Expression<String> address = from.get("address");
		Expression<String> name = from.get("name");
		Expression<Double> price = from.get("price");

		Predicate predicate = criteriaBuilder.or(
				criteriaBuilder.like(criteriaBuilder.lower(city), pattern),
				criteriaBuilder.like(criteriaBuilder.lower(zip), pattern),
				criteriaBuilder.like(criteriaBuilder.lower(address), pattern),
				criteriaBuilder.like(criteriaBuilder.lower(name), pattern));

		NewRelic.addCustomParameter("searchMaxPrice", criteria.getMaximumPrice()); 
		if (criteria.getMaximumPrice() > 0) {
			predicate = criteriaBuilder.and(predicate, criteriaBuilder.lessThanOrEqualTo(price, criteria.getMaximumPrice()));
		}

		hotelCriteriaQuery.where(predicate);

		TypedQuery<Hotel> typedQuery = em.createQuery(hotelCriteriaQuery);

		if (criteria.getPage() > 0 && criteria.getPageSize() > 0)
			typedQuery.setMaxResults(criteria.getPageSize()).setFirstResult(criteria.getPage() * criteria.getPageSize());

		List<Hotel> hotels = typedQuery.getResultList();

		
		log.debug("returned " + hotels.size() + " results");
		NewRelic.addCustomParameter("searchResultSize",hotels.size() );
		
		
		//	**************************************************************
		//    Adding a Call to RabbitMQ to remove the searchCriteria from the queue
		//  **************************************************************
		try{
			URL url= new URL("http://nr-abbitmq-spring.cfapps.io/get");
		    HttpURLConnection con = (HttpURLConnection) url.openConnection();
		    con.setRequestMethod("POST");
		    con.setDoOutput(true);
		    //con.getOutputStream().write(Data.getBytes("UTF-8"));
		    con.getInputStream();
		    con.disconnect();
		} catch(Exception ex) {
			
		}

		/*		if(Http.post("http://nr-abbitmq-spring.cfapps.io/get").text().equals("OK")){
		    //..success
		}else{
		    //failure
		}
*/
		//    **************************************************************

		return hotels;
	}

	public void slowThisDown(int delay){
		
		//and now let's wait a while if it's in the first 10 minutes of the hour
		//    unless the user specified a longer delay
		try {
			Calendar now = Calendar.getInstance();
		    if( now.get(Calendar.MINUTE)<10 ){
		    	if(delay != 1000)
		    			delay = 7500;
		    }
			Thread.sleep(delay);				// slow down the app and wait a while 
		} catch(InterruptedException ex) {
			Thread.currentThread().interrupt();
		}
      return;
	}
	
	public void throwAnException(String msg){	
		//Just for fun, let's thow an Exception
		try {
			log.debug("Intentional Exception Thrown: " + msg);
			throw new Exception(msg);
			
		} catch(Exception ex) {
			log.debug("Intentional Exception Thrown: " + msg);
		}
      return;
	}


	@Transactional(readOnly = true)
	public Hotel findHotelById(Long id) {
		
		NewRelic.setTransactionName("Web", "Find Hotel By Id");
		
		slowThisDown(0);
		return em.find(Hotel.class, id);
		
	}

	@Transactional
	public Booking createBooking(Long hotelId, String username) {
		
		NewRelic.setTransactionName("Web", "Create Booking");
		
		Hotel hotel = em.find(Hotel.class, hotelId);
		User user = findUser(username);
		Booking booking = new Booking(hotel, user);
		em.persist(booking);
		slowThisDown(0);
				
		return booking;
	}



	@Override
	@Transactional
	public void persistBooking(Booking booking) {
		
		NewRelic.setTransactionName("Web", "Book Hotel");
		NewRelic.recordMetric("Custom/Booking Revenue", Integer.valueOf(booking.getTotal().intValue()) );
		
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
		
		em.merge(booking);
	
		NewRelic.addCustomParameter("BookingNumberOfNights",booking.getNights() );
		NewRelic.addCustomParameter("BookingRevenue",Integer.valueOf(booking.getTotal().intValue()) );
		NewRelic.addCustomParameter("BookingRate",Integer.valueOf(booking.getHotel().getPrice().intValue() ) );

		NewRelic.addCustomParameter("BookingHotelName",booking.getHotel().getName() +" - " + booking.getHotel().getCity() + ", " + booking.getHotel().getState() );
		NewRelic.addCustomParameter("BookingCity", booking.getHotel().getCity() + ", " + booking.getHotel().getState() );
		NewRelic.addCustomParameter("BookingState", booking.getHotel().getState() );
		NewRelic.addCustomParameter("BookingZip", booking.getHotel().getZip() );

		NewRelic.addCustomParameter("BookingSmokingRoom",(booking.isSmoking()) ? "Smoking" : "Non-Smoking" );
		NewRelic.addCustomParameter("BookingNumberOfBeds",booking.getBeds() );
		NewRelic.addCustomParameter("BookingAgent",booking.getUser().getName() );

	}

	@Transactional
	public void cancelBooking(Long id) {
		
		NewRelic.setTransactionName("Web", "Cancel Booking");
		
		Booking booking = em.find(Booking.class, id);
		if (booking != null) {
			em.refresh(booking);
			em.remove(booking);
						
		}
	}

	@Transactional(readOnly = true)
	public void deleteAllBookings() {
			em.createQuery("delete from Booking ").executeUpdate();
	}


	
	
	
	// helpers

	private String getSearchPattern(SearchCriteria criteria) {
		if (StringUtils.hasText(criteria.getSearchString())) {
			return "%"
					+ criteria.getSearchString().toLowerCase()
					.replace('*', '%') + "%";
		} else {
			return "'%'";
		}
	}

	public User findUser(String username) {
		return (User) em.createQuery(
				"select u from User u where u.username = :username")
				.setParameter("username", username).getSingleResult();
	}

	@Override
	public User login(String u, String pw) {
		return findUser( u) ;
	 //	return (User) em.createQuery("select u from User u where u.username = :u and u.password = :p").setParameter("u", u).setParameter("p",pw).getSingleResult();
	}
}
