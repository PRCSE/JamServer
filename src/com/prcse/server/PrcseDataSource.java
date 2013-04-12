package com.prcse.server;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Observable;
import java.util.ResourceBundle;

import com.prcse.datamodel.Account;
import com.prcse.datamodel.Artist;
import com.prcse.datamodel.Billing;
import com.prcse.datamodel.Customer;
import com.prcse.datamodel.Event;
import com.prcse.datamodel.Favourite;
import com.prcse.datamodel.SeatingPlan;
import com.prcse.datamodel.Tour;
import com.prcse.datamodel.Venue;
import com.prcse.protocol.CustomerInfo;
import com.prcse.utils.PrcseSource;

public class PrcseDataSource extends Observable implements PrcseSource {
	
	/*   Class Variables   */
	private Connection connection;	// Connection to the database
	private ResourceBundle queries;
	
	public PrcseDataSource(Connection connection, ResourceBundle queries) {
		super();
		this.connection = connection;
		this.queries = queries;
	}
	
	@Override
	public void connect() throws Exception {
		// would be used for a single direct connection (now uses connection pool)
		throw new Exception("not implemeneted");
	}

	@Override
	public void disconnect() throws Exception {
		this.connection.close();
		this.connection = null;
		changed();
		System.out.println("Connection returned to pool/closed.");
	}

	@Override
	public boolean isConnected() {
		return this.connection != null;
	}
	
	// Update observers
	protected void changed() {
		setChanged();
		notifyObservers();
		clearChanged();
	}

	@Override
	public ArrayList<Object> getFrontPage() throws Exception {
		ArrayList result = new ArrayList();
		HashMap<Long, Artist> artists = new HashMap<Long, Artist>();
		HashMap<Long, Event> events = new HashMap<Long, Event>();
		HashMap<Long, Venue> venues = new HashMap<Long, Venue>();
		HashMap<Long, Tour> tours = new HashMap<Long, Tour>();
		HashMap<Long, SeatingPlan> seatingPlans = new HashMap<Long, SeatingPlan>();
		Artist a = null;
		
		String query = queries.getString("artists_sql");
		PreparedStatement stmt = this.connection.prepareStatement(query);
		
		ResultSet rs = stmt.executeQuery();
		
		while (rs.next()){
			a = new Artist(rs.getLong("id"), 
									rs.getString("name"), 
									rs.getString("bio"), 
									rs.getString("genres"),
									rs.getString("thumb_image"),
									rs.getString("header_image"));
			result.add(a);
			artists.put(new Long(a.getId()), a);
		}
		
		rs.close();
		stmt.close();
		
		query = queries.getString("events_sql");
		stmt = this.connection.prepareStatement(query);
		
		rs = stmt.executeQuery();
		
		while (rs.next()){
			Venue v = venues.get(new Long(rs.getLong("venue_id")));
			if(v == null) {
				v = new Venue(rs.getLong("venue_id"), 
								rs.getString("venue_name"));
				venues.put(new Long(v.getId()), v);
			}
			
			SeatingPlan sp = seatingPlans.get(new Long(rs.getLong("venue_id")));
			if(sp == null) {
				sp = new SeatingPlan(rs.getLong("seating_plan_id"), 
								rs.getString("seating_plan_name"),
								v);
				seatingPlans.put(new Long(sp.getId()), sp);
			}
			
			Event e = events.get(new Long(rs.getLong("event_id")));
			if(e == null) {
				e = new Event(rs.getLong("event_id"), 
								rs.getString("event_name"), 
								rs.getDate("start_time"),
								rs.getDate("end_time"));
				events.put(new Long(e.getId()), e);
				sp.addEvent(e);
				e.setSeatingPlan(sp);
			}
			
			a = artists.get(new Long(rs.getLong("artist_id")));
			
			if(rs.getLong("tour_id") != 0){
				Tour t = tours.get(new Long(rs.getLong("tour_id")));
				if(t == null) {
					t = new Tour(rs.getLong("tour_id"), 
									rs.getString("tour_name"), 
									a);
					tours.put(new Long(t.getId()), t);
					a.addTour(t);
				}
			}
			
			Billing b = new Billing(rs.getLong("billing_id"), 
												a,
												e,
												rs.getInt("lineup_order"));
			
			a.addBilling(b);
			e.addBilling(b);
		}
		
		rs.close();
		stmt.close();
		
		return result;
	}

	@Override
	public CustomerInfo login(CustomerInfo request) throws Exception {
		
		String query = queries.getString("customer_sql");
		PreparedStatement stmt = this.connection.prepareStatement(query);
		stmt.setString(1, request.getEmail());
		stmt.setString(2, request.getPassword());
		
		ResultSet rs = stmt.executeQuery();
		
		if(rs.next()) {
			// construct customer and account from result set
			Customer customer = new Customer(rs.getString("email"),
												rs.getString("token"),
												rs.getString("title"),
												rs.getString("forename"),
												rs.getString("surname"),
												rs.getString("telephone"),
												rs.getString("mobile"),
												rs.getString("line1"),
												rs.getString("line2"),
												rs.getString("town"),
												rs.getString("county"),
												rs.getString("postcode"),
												rs.getString("country"),
												rs.getDate("created"),
												false);			
			
			customer.setId(rs.getLong("customer_id"));
			customer.getAccount().setId(rs.getLong("account_id"));
			request.setCustomer(customer);
			request.setPassword(null);
			
			query = queries.getString("favourites_sql");
			PreparedStatement stmt2 = this.connection.prepareStatement(query);
			stmt2.setLong(1, customer.getId());
			
			ResultSet rs2 = stmt2.executeQuery();
			
			ArrayList<Favourite> favourites = new ArrayList<Favourite>();
			
			while(rs2.next()) {
				//set favourites from statement
				favourites.add(new Favourite(rs2.getLong("id"),
												rs2.getLong("customer_id"),
												rs2.getLong("artist_id"),
												rs2.getLong("venue_id"),
												rs2.getLong("genre_id"),
												rs2.getLong("event_id")));
			}
			
			request.setFavourites(favourites);
			
			rs2.close();
			stmt2.close();
		}
		else {
			throw new Exception("Account does not exsist or password is incorrect.");
		}
		
		rs.close();
		stmt.close();
		
		return request;
	}

	@Override
	public CustomerInfo syncCustomer(CustomerInfo request) throws Exception {
		
		// TODO also make sure to redo salt and hash for password when email changed
		
		String query = queries.getString("account_sql");
		PreparedStatement stmt = this.connection.prepareStatement(query);
		stmt.setLong(1, request.getCustomer().getAccount().getId());
		
		ResultSet rs = stmt.executeQuery();
		
		if(rs.next()) {
			updateCustomer(request.getCustomer(), request.getFavourites());
		}
		else {
			insertCustomer(request.getCustomer());
		}
		
		rs.close();
		stmt.close();
		
		return request;
	}

	private void insertCustomer(Customer customer) throws SQLException, Exception {
		// run insert
		String query = queries.getString("insert_account_sql");
		PreparedStatement stmt2 = this.connection.prepareStatement(query, Statement.RETURN_GENERATED_KEYS);
		stmt2.setString(1, customer.getAccount().getEmail());
		stmt2.setString(2, customer.getAccount().getToken());
		stmt2.setString(3, null);
		
		// execute and set account id
		stmt2.execute();
		ResultSet rs2 = stmt2.getGeneratedKeys();
		if (rs2.next()){
			customer.getAccount().setId(rs2.getLong(1));
		}
		else {
			throw new Exception("Failed to create account. Please try again.");
		}
		
		// close first commit
		rs2.close();
		stmt2.close();
		
		query = queries.getString("insert_customer_sql");
		stmt2 = this.connection.prepareStatement(query, Statement.RETURN_GENERATED_KEYS);
		stmt2.setString(1, customer.getTitle());
		stmt2.setString(2, customer.getForename());
		stmt2.setString(3, customer.getSurname());
		stmt2.setString(4, customer.getTelephone());
		stmt2.setString(5, customer.getMobile());
		stmt2.setString(6, customer.getAddr1());
		stmt2.setString(7, customer.getAddr2());
		stmt2.setString(8, customer.getTown());
		stmt2.setString(9, customer.getCounty());
		stmt2.setString(10, customer.getPostcode());
		stmt2.setString(11, customer.getCountry());
		stmt2.setString(12, customer.createdAsString());
		stmt2.setLong(13, customer.getAccount().getId());
		
		stmt2.execute();
		rs2 = stmt2.getGeneratedKeys();
		if (rs2.next()){
			customer.setId(rs2.getLong(1));
		}
		else {
			throw new Exception("Failed to create customer. Please try again.");
		}
		
		// close second commit
		rs2.close();
		stmt2.close();
	}

	private void updateCustomer(Customer customer, ArrayList<Favourite> favourites) throws SQLException, Exception {
		// run update
		String query = queries.getString("update_account_sql");
		PreparedStatement stmt2 = this.connection.prepareStatement(query);
		stmt2.setString(1, customer.getTitle());
		stmt2.setString(2, customer.getForename());
		stmt2.setString(3, customer.getSurname());
		stmt2.setString(4, customer.getTelephone());
		stmt2.setString(5, customer.getMobile());
		stmt2.setString(6, customer.getAddr1());
		stmt2.setString(7, customer.getAddr2());
		stmt2.setString(8, customer.getTown());
		stmt2.setString(9, customer.getCounty());
		stmt2.setString(10, customer.getPostcode());
		stmt2.setString(11, customer.getCountry());
		stmt2.setString(12, customer.getAccount().getEmail());
		stmt2.setString(13, customer.getAccount().getToken());
		stmt2.setLong(14, customer.getAccount().getId());
		
		
		stmt2.execute();
		stmt2.close();
		
		// make a copy of the current db favourites
		ArrayList<Favourite> dbFavourites = new ArrayList<Favourite>();
		
		query = queries.getString("favourites_sql");
		stmt2 = this.connection.prepareStatement(query);
		stmt2.setLong(1, customer.getId());
		
		ResultSet rs2 = stmt2.executeQuery();
		
		while(rs2.next()) {
			//set favourites from statement
			dbFavourites.add(new Favourite(rs2.getLong("id"),
											rs2.getLong("customer_id"),
											rs2.getLong("artist_id"),
											rs2.getLong("venue_id"),
											rs2.getLong("genre_id"),
											rs2.getLong("event_id")));
		}
		
		//compare current to new favourites
		
		//if not in db (id = 0) insert
		for(int i=0; i < favourites.size(); i++) {
			if(favourites.get(i).getId() == 0) {
				query = queries.getString("insert_favourites_sql");
				stmt2 = this.connection.prepareStatement(query, Statement.RETURN_GENERATED_KEYS);
				stmt2.setLong(1, customer.getId());
				stmt2.setLong(2, favourites.get(i).getArtistId());
				stmt2.setLong(3, favourites.get(i).getEventId());
				stmt2.setLong(4, favourites.get(i).getGenreId());
				stmt2.setLong(5, favourites.get(i).getVenueId());
				
				
				stmt2.execute();
				rs2 = stmt2.getGeneratedKeys();
				if (rs2.next()){
					favourites.get(i).setId(rs2.getLong(1));
				}
				else {
					throw new Exception("Failed to create favourites. Try again later.");
				}
				
				rs2.close();
				stmt2.close();
			}
		}
		
		
		// list to hold favourites to be deleted
		ArrayList<Favourite> deleteList = new ArrayList<Favourite>();
		
		// if favourite not in current favourites list add to delete list
		for(int i=0; i < dbFavourites.size(); i++) {
			boolean toDelete = true;
			
			for(int j=0; j < favourites.size(); j++) {
				if(favourites.get(j).getId() == dbFavourites.get(i).getId()) {
					toDelete = false;
					break;
				}
			}
			
			if(toDelete == true) {
				deleteList.add(dbFavourites.get(i));
			}
		}
		
		// delete all favourites on delete list
		for(int i=0; i < deleteList.size(); i++) {
			query = queries.getString("delete_favourites_sql");
			stmt2 = this.connection.prepareStatement(query);
			stmt2.setLong(1, customer.getId());
			stmt2.setLong(2, deleteList.get(i).getId());
			
			stmt2.execute();
			stmt2.close();
		}
	}
}
