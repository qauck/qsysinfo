/********************************************************************************
 * (C) Copyright 2000-2009, by Shawn Qualia.
 *
 * This library is free software; you can redistribute it and/or modify it 
 * under the terms of the GNU Lesser General Public License as published by 
 * the Free Software Foundation; either version 2.1 of the License, or 
 * (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful, but 
 * WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY 
 * or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public 
 * License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License 
 * along with this library; if not, write to the Free Software Foundation, 
 * Inc., 59 Temple Place, Suite 330, Boston, MA 02111-1307, USA.
 *
 * [Java is a trademark or registered trademark of Sun Microsystems, Inc. 
 * in the United States and other countries.]
 ********************************************************************************/

package org.uguess.android.sysinfo;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserFactory;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ListActivity;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.DialogInterface.OnClickListener;
import android.content.SharedPreferences.Editor;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceCategory;
import android.preference.PreferenceScreen;
import android.text.ClipboardManager;
import android.text.Html;
import android.text.TextUtils;
import android.text.method.LinkMovementMethod;
import android.util.Log;
import android.view.ContextMenu;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.view.ContextMenu.ContextMenuInfo;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.AdapterView.AdapterContextMenuInfo;
import android.widget.AdapterView.OnItemClickListener;

/**
 * NetStateManager
 */
public final class NetStateManager extends ListActivity
{

	private static final String PREF_KEY_REFRESH_INTERVAL = "refresh_interval"; //$NON-NLS-1$
	private static final String PREF_KEY_REMOTE_QUERY = "remote_query"; //$NON-NLS-1$

	private static final int REFRESH_HIGH = 0;
	private static final int REFRESH_NORMAL = 1;
	private static final int REFRESH_LOW = 2;
	private static final int REFRESH_PAUSED = 3;

	private static final int ENABLED = 0;
	private static final int DISABLED = 1;
	private static final int WIFI_ONLY = 2;

	private static final int MSG_IP_READY = 1;
	private static final int MSG_DISMISS_PROGRESS = 2;

	private static final String[] SOCKET_STATES = new String[]{
			"ESTABLISHED", //$NON-NLS-1$
			"SYN_SENT", //$NON-NLS-1$
			"SYN_RECV", //$NON-NLS-1$
			"FIN_WAIT1", //$NON-NLS-1$
			"FIN_WAIT2", //$NON-NLS-1$
			"TIME_WAIT", //$NON-NLS-1$
			"CLOSE", //$NON-NLS-1$
			"CLOSE_WAIT", //$NON-NLS-1$
			"LAST_ACK", //$NON-NLS-1$
			"LISTEN", //$NON-NLS-1$
			"CLOSING" //$NON-NLS-1$
	};

	private ConnectionItem dummyInfo;

	private HashMap<String, IpInfo> queryCache;

	private ProgressDialog progress;

	private volatile boolean aborted;

	private Handler handler = new Handler( ) {

		public void handleMessage( android.os.Message msg )
		{
			switch ( msg.what )
			{
				case MSG_IP_READY :

					if ( aborted )
					{
						return;
					}

					final IpInfo info = (IpInfo) msg.obj;

					if ( info != null
							&& !TextUtils.isEmpty( info.latitude )
							&& !TextUtils.isEmpty( info.longitude ) )
					{

						OnClickListener listener = new OnClickListener( ) {

							public void onClick( DialogInterface dialog,
									int which )
							{
								Intent it = new Intent( Intent.ACTION_VIEW );

								it.setData( Uri.parse( "geo:0,0?q=" //$NON-NLS-1$
										+ info.latitude
										+ "," //$NON-NLS-1$
										+ info.longitude
										+ "&z=8" ) ); //$NON-NLS-1$

								it = Intent.createChooser( it, null );

								startActivity( it );
							}
						};

						TextView txt = new TextView( NetStateManager.this );
						txt.setPadding( 15, 0, 15, 0 );
						txt.setTextAppearance( NetStateManager.this,
								android.R.style.TextAppearance_Medium );

						txt.setText( Html.fromHtml( getString( R.string.location_info,
								info.ip,
								info.host == null ? "" : ( "<a href=\"http://" //$NON-NLS-1$ //$NON-NLS-2$
										+ info.host
										+ "\">" //$NON-NLS-1$
										+ info.host + "</a>" ), //$NON-NLS-1$
								info.country == null ? "" : info.country, //$NON-NLS-1$
								info.region == null ? "" : info.region, //$NON-NLS-1$
								info.city == null ? "" : info.city ) ) ); //$NON-NLS-1$
						txt.setMovementMethod( LinkMovementMethod.getInstance( ) );

						new AlertDialog.Builder( NetStateManager.this ).setTitle( R.string.ip_location )
								.setPositiveButton( R.string.view_map, listener )
								.setNegativeButton( R.string.close, null )
								.setView( txt )
								.create( )
								.show( );
					}
					else
					{
						Toast.makeText( NetStateManager.this,
								R.string.no_ip_info,
								Toast.LENGTH_SHORT ).show( );
					}
					break;
				case MSG_DISMISS_PROGRESS :

					if ( progress != null )
					{
						progress.dismiss( );
						progress = null;
					}
					break;
			}
		};
	};

	private Runnable task = new Runnable( ) {

		public void run( )
		{
			refresh( );

			int interval = getRefreshInterval( );

			switch ( interval )
			{
				case REFRESH_HIGH :
					handler.postDelayed( this, 1000 );
					break;
				case REFRESH_NORMAL :
					handler.postDelayed( this, 2000 );
					break;
				case REFRESH_LOW :
					handler.postDelayed( this, 4000 );
					break;
			}
		}
	};

	@Override
	protected void onCreate( Bundle savedInstanceState )
	{
		super.onCreate( savedInstanceState );

		queryCache = new HashMap<String, IpInfo>( );

		dummyInfo = new ConnectionItem( );
		dummyInfo.proto = getString( R.string.protocol );
		dummyInfo.ip = getString( R.string.local_remote_addr );
		dummyInfo.state = getString( R.string.state );

		registerForContextMenu( getListView( ) );

		getListView( ).setOnItemClickListener( new OnItemClickListener( ) {

			public void onItemClick( AdapterView<?> parent, View view,
					int position, long id )
			{
				if ( position > 0 )
				{
					int state = getRemoteQueryState( );

					if ( state == DISABLED )
					{
						return;
					}
					else if ( state == WIFI_ONLY )
					{
						ConnectivityManager cm = (ConnectivityManager) getSystemService( Activity.CONNECTIVITY_SERVICE );

						NetworkInfo info = cm.getNetworkInfo( ConnectivityManager.TYPE_WIFI );

						if ( info == null || !info.isConnected( ) )
						{
							return;
						}
					}

					ConnectionItem itm = (ConnectionItem) parent.getItemAtPosition( position );

					String ip = getValidIP( itm.remote );

					if ( !TextUtils.isEmpty( ip ) )
					{
						queryIPInfo( ip );
					}
					else
					{
						Toast.makeText( NetStateManager.this,
								R.string.no_ip_info,
								Toast.LENGTH_SHORT ).show( );
					}
				}
			}
		} );

		ArrayAdapter<ConnectionItem> adapter = new ArrayAdapter<ConnectionItem>( this,
				R.layout.net_item ) {

			public android.view.View getView( int position,
					android.view.View convertView, android.view.ViewGroup parent )
			{
				View view;
				TextView txt_proto, txt_ip, txt_state;

				if ( convertView == null )
				{
					view = NetStateManager.this.getLayoutInflater( )
							.inflate( R.layout.net_item, parent, false );
				}
				else
				{
					view = convertView;
				}

				ConnectionItem itm = getItem( position );

				txt_proto = (TextView) view.findViewById( R.id.txt_proto );
				txt_ip = (TextView) view.findViewById( R.id.txt_ip );
				txt_state = (TextView) view.findViewById( R.id.txt_state );

				txt_proto.setText( itm.proto );
				txt_ip.setText( itm.ip );
				txt_state.setText( itm.state );

				if ( itm == dummyInfo )
				{
					setFont( txt_proto, Typeface.BOLD );
					setFont( txt_ip, Typeface.BOLD );
					setFont( txt_state, Typeface.BOLD );

					txt_proto.setTextColor( Color.WHITE );
					txt_ip.setTextColor( Color.WHITE );
					txt_state.setTextColor( Color.WHITE );
				}
				else
				{
					txt_proto.setTextAppearance( NetStateManager.this,
							android.R.style.TextAppearance_Small );
					txt_ip.setTextAppearance( NetStateManager.this,
							android.R.style.TextAppearance_Small );
					txt_state.setTextAppearance( NetStateManager.this,
							android.R.style.TextAppearance_Small );
				}

				return view;
			}
		};

		getListView( ).setAdapter( adapter );
	}

	@Override
	protected void onResume( )
	{
		aborted = false;

		super.onResume( );

		handler.post( task );
	}

	@Override
	protected void onPause( )
	{
		aborted = true;

		handler.removeCallbacks( task );
		handler.removeMessages( MSG_IP_READY );

		super.onPause( );
	}

	@Override
	public boolean onCreateOptionsMenu( Menu menu )
	{
		MenuItem mi = menu.add( Menu.NONE,
				R.id.mi_preference,
				Menu.NONE,
				R.string.preference );
		mi.setIcon( android.R.drawable.ic_menu_preferences );

		return true;
	}

	@Override
	public boolean onOptionsItemSelected( MenuItem item )
	{
		if ( item.getItemId( ) == R.id.mi_preference )
		{
			Intent it = new Intent( Intent.ACTION_VIEW );
			it.setClass( this, NetStateSettings.class );

			it.putExtra( PREF_KEY_REFRESH_INTERVAL, getRefreshInterval( ) );
			it.putExtra( PREF_KEY_REMOTE_QUERY, getRemoteQueryState( ) );

			startActivityForResult( it, 1 );

			return true;
		}
		return false;
	}

	@Override
	public void onCreateContextMenu( ContextMenu menu, View v,
			ContextMenuInfo menuInfo )
	{
		int pos = ( (AdapterContextMenuInfo) menuInfo ).position;

		if ( pos > 0 )
		{
			menu.setHeaderTitle( R.string.actions );
			menu.add( R.string.copy_ip );
		}
	}

	@Override
	public boolean onContextItemSelected( MenuItem item )
	{
		int pos = ( (AdapterContextMenuInfo) item.getMenuInfo( ) ).position;

		if ( pos > 0 )
		{
			ConnectionItem itm = (ConnectionItem) getListView( ).getItemAtPosition( pos );

			if ( itm != null && itm.remote != null )
			{
				ClipboardManager cm = (ClipboardManager) getSystemService( CLIPBOARD_SERVICE );

				if ( cm != null )
				{
					cm.setText( itm.remote );
				}
			}

			return true;
		}

		return false;
	}

	@Override
	protected void onActivityResult( int requestCode, int resultCode,
			Intent data )
	{
		if ( requestCode == 1 )
		{
			int interval = data.getIntExtra( PREF_KEY_REFRESH_INTERVAL,
					REFRESH_LOW );
			if ( interval != getRefreshInterval( ) )
			{
				setRefreshInterval( interval );
			}

			int state = data.getIntExtra( PREF_KEY_REMOTE_QUERY, ENABLED );
			if ( state != getRemoteQueryState( ) )
			{
				setRemoteQueryState( state );
			}
		}
	}

	private String getValidIP( String ip )
	{
		if ( ip != null )
		{
			int idx = ip.lastIndexOf( ':' );

			if ( idx != -1 )
			{
				ip = ip.substring( 0, idx ).trim( );
			}

			if ( !"0.0.0.0".equals( ip ) && !"127.0.0.1".equals( ip ) ) //$NON-NLS-1$ //$NON-NLS-2$
			{
				return ip;
			}
		}

		return null;
	}

	private void queryIPInfo( final String ip )
	{
		IpInfo info = queryCache.get( ip );

		if ( info != null )
		{
			handler.sendMessage( handler.obtainMessage( MSG_IP_READY, info ) );
			return;
		}

		if ( progress == null )
		{
			progress = new ProgressDialog( this );
		}
		progress.setMessage( getString( R.string.query_ip_msg ) );
		progress.setIndeterminate( true );
		progress.show( );

		new Thread( new Runnable( ) {

			public void run( )
			{
				IpInfo info = new IpInfo( );
				info.ip = ip;

				info = getIpInfo( info );

				queryCache.put( ip, info );

				handler.sendEmptyMessage( MSG_DISMISS_PROGRESS );

				handler.sendMessage( handler.obtainMessage( MSG_IP_READY, info ) );
			}
		} ).start( );
	}

	private void refresh( )
	{
		ArrayList<ConnectionItem> data = new ArrayList<ConnectionItem>( );

		data.add( dummyInfo );

		ArrayList<ConnectionItem> items = readStatesRaw( );

		if ( items != null )
		{
			data.addAll( items );
		}

		ArrayAdapter<ConnectionItem> adapter = (ArrayAdapter<ConnectionItem>) getListView( ).getAdapter( );

		adapter.setNotifyOnChange( false );

		adapter.clear( );

		for ( ConnectionItem ci : data )
		{
			adapter.add( ci );
		}

		adapter.notifyDataSetChanged( );

		if ( adapter.getCount( ) == 1 )
		{
			Log.d( NetStateManager.class.getName( ),
					"No network traffic detected" ); //$NON-NLS-1$
		}
	}

	private void setFont( TextView txt, int type )
	{
		if ( txt.getTypeface( ) == null
				|| txt.getTypeface( ).getStyle( ) != type )
		{
			txt.setTypeface( Typeface.DEFAULT, type );
		}
	}

	private ArrayList<ConnectionItem> readStatesRaw( )
	{
		ArrayList<ConnectionItem> tcp = parseRawData( "TCP", "/proc/net/tcp", false ); //$NON-NLS-1$ //$NON-NLS-2$
		ArrayList<ConnectionItem> udp = parseRawData( "UDP", "/proc/net/udp", true ); //$NON-NLS-1$ //$NON-NLS-2$

		if ( tcp == null )
		{
			return udp;
		}
		else if ( udp != null )
		{
			tcp.addAll( udp );
		}

		return tcp;
	}

	private ArrayList<ConnectionItem> parseRawData( String proto,
			String source, boolean ignoreState )
	{
		BufferedReader reader = null;
		try
		{
			reader = new BufferedReader( new InputStreamReader( new FileInputStream( source ) ),
					4096 );

			ArrayList<ConnectionItem> itms = new ArrayList<ConnectionItem>( );
			boolean first = true;
			int localOffset = -1, remOffset = -1, stateOffset = -1, stateEndOffset = -1;
			String line;

			while ( ( line = reader.readLine( ) ) != null )
			{
				if ( first )
				{
					localOffset = line.indexOf( "local_address" ); //$NON-NLS-1$
					remOffset = line.indexOf( "rem_address" ); //$NON-NLS-1$
					stateOffset = line.indexOf( "st", remOffset ); //$NON-NLS-1$
					stateEndOffset = line.indexOf( ' ', stateOffset );

					if ( localOffset == -1
							|| remOffset == -1
							|| stateOffset == -1
							|| stateEndOffset == -1 )
					{
						Log.e( NetStateManager.class.getName( ), "Unexpected " //$NON-NLS-1$
								+ proto
								+ " header format: " //$NON-NLS-1$
								+ line );

						break;
					}

					first = false;
				}
				else
				{
					ConnectionItem ci = new ConnectionItem( );

					ci.proto = proto;

					String local = parseRawIP( line.substring( localOffset,
							remOffset ).trim( ) );

					ci.remote = parseRawIP( line.substring( remOffset,
							stateOffset ).trim( ) );

					ci.ip = local + '\n' + ci.remote;

					if ( !ignoreState )
					{
						int st = Integer.parseInt( line.substring( stateOffset,
								stateEndOffset ).trim( ), 16 );

						ci.state = "Unknown"; //$NON-NLS-1$

						if ( st > 0 && st <= SOCKET_STATES.length )
						{
							ci.state = SOCKET_STATES[st - 1];
						}
					}

					itms.add( ci );
				}
			}

			return itms;
		}
		catch ( Exception e )
		{
			Log.e( NetStateManager.class.getName( ),
					e.getLocalizedMessage( ),
					e );
		}
		finally
		{
			if ( reader != null )
			{
				try
				{
					reader.close( );
				}
				catch ( IOException e )
				{
					Log.e( NetStateManager.class.getName( ),
							e.getLocalizedMessage( ),
							e );
				}
			}
		}

		return null;
	}

	private String parseRawIP( String raw )
	{
		if ( !TextUtils.isEmpty( raw ) )
		{
			int idx = raw.lastIndexOf( ':' );

			if ( idx != -1 )
			{
				String port = raw.substring( idx + 1 ).trim( );
				String ip = raw.substring( 0, idx ).trim( );

				try
				{
					int pt = Integer.parseInt( port, 16 );

					if ( pt == 0 )
					{
						port = "*"; //$NON-NLS-1$
					}
					else
					{
						port = String.valueOf( pt );
					}
				}
				catch ( Exception e )
				{
					port = "?"; //$NON-NLS-1$

					Log.e( NetStateManager.class.getName( ),
							"Parsing raw port fail : " + raw ); //$NON-NLS-1$
				}

				if ( ip.length( ) != 8 )
				{
					Log.e( NetStateManager.class.getName( ),
							"Parsing raw ip fail : " + raw ); //$NON-NLS-1$
				}
				else
				{
					try
					{
						int n1 = Integer.parseInt( ip.substring( 6 ), 16 );
						int n2 = Integer.parseInt( ip.substring( 4, 6 ), 16 );
						int n3 = Integer.parseInt( ip.substring( 2, 4 ), 16 );
						int n4 = Integer.parseInt( ip.substring( 0, 2 ), 16 );

						ip = n1 + "." + n2 + "." + n3 + "." + n4; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
					}
					catch ( Exception e )
					{
						ip = "?"; //$NON-NLS-1$

						Log.e( NetStateManager.class.getName( ),
								"Parsing raw ip fail : " + raw ); //$NON-NLS-1$
					}

				}

				return ip + ':' + port;
			}
		}

		return raw;
	}

	private int getRefreshInterval( )
	{
		SharedPreferences sp = getPreferences( Context.MODE_PRIVATE );

		return sp.getInt( PREF_KEY_REFRESH_INTERVAL, REFRESH_LOW );
	}

	private void setRefreshInterval( int interval )
	{
		SharedPreferences sp = getPreferences( Context.MODE_PRIVATE );

		Editor et = sp.edit( );
		et.putInt( PREF_KEY_REFRESH_INTERVAL, interval );
		et.commit( );
	}

	private int getRemoteQueryState( )
	{
		SharedPreferences sp = getPreferences( Context.MODE_PRIVATE );

		return sp.getInt( PREF_KEY_REMOTE_QUERY, ENABLED );
	}

	private void setRemoteQueryState( int state )
	{
		SharedPreferences sp = getPreferences( Context.MODE_PRIVATE );

		Editor et = sp.edit( );
		et.putInt( PREF_KEY_REMOTE_QUERY, state );
		et.commit( );
	}

	static IpInfo getIpInfo( IpInfo info )
	{
		if ( info == null )
		{
			info = new IpInfo( );
		}

		InputStream input = null;

		try
		{
			URL url;

			if ( info.ip == null )
			{
				url = new URL( "http://ipinfodb.com/ip_query.php" ); //$NON-NLS-1$
			}
			else
			{
				url = new URL( "http://ipinfodb.com/ip_query.php?ip=" //$NON-NLS-1$
						+ info.ip );
			}

			XmlPullParser parser = XmlPullParserFactory.newInstance( )
					.newPullParser( );

			input = url.openStream( );

			parser.setInput( input, null );

			String name, value;
			while ( parser.next( ) != XmlPullParser.END_DOCUMENT )
			{
				if ( parser.getEventType( ) == XmlPullParser.START_TAG )
				{
					name = parser.getName( );

					if ( info.ip == null && "Ip".equals( name ) ) //$NON-NLS-1$
					{
						info.ip = parser.nextText( );
					}
					else if ( "Status".equals( name ) ) //$NON-NLS-1$
					{
						value = parser.nextText( );

						if ( !"OK".equals( value ) ) //$NON-NLS-1$
						{
							Log.d( NetStateManager.class.getName( ),
									"Invalid ip?: " + info.ip ); //$NON-NLS-1$

							break;
						}
					}
					else if ( "CountryName".equals( name ) ) //$NON-NLS-1$
					{
						value = parser.nextText( );

						if ( "Reserved".equals( value ) ) //$NON-NLS-1$
						{
							Log.d( NetStateManager.class.getName( ),
									"Reserved ip?: " + info.ip ); //$NON-NLS-1$

							break;
						}

						info.country = value;
					}
					else if ( "RegionName".equals( name ) ) //$NON-NLS-1$
					{
						info.region = parser.nextText( );
					}
					else if ( "City".equals( name ) ) //$NON-NLS-1$
					{
						info.city = parser.nextText( );
					}
					else if ( "Latitude".equals( name ) ) //$NON-NLS-1$
					{
						info.latitude = parser.nextText( );
					}
					else if ( "Longitude".equals( name ) ) //$NON-NLS-1$
					{
						info.longitude = parser.nextText( );
					}
				}
			}

			if ( info.ip != null )
			{
				String host = InetAddress.getByName( info.ip ).getHostName( );

				if ( !info.ip.equals( host ) )
				{
					info.host = host;
				}
			}
		}
		catch ( Exception e )
		{
			Log.e( NetStateManager.class.getName( ),
					e.getLocalizedMessage( ),
					e );
		}
		finally
		{
			if ( input != null )
			{
				try
				{
					input.close( );
				}
				catch ( IOException e )
				{
					Log.e( NetStateManager.class.getName( ),
							e.getLocalizedMessage( ),
							e );
				}
			}
		}

		return info;
	}

	/**
	 * NetStateSettings
	 */
	public static final class NetStateSettings extends PreferenceActivity
	{

		@Override
		protected void onCreate( Bundle savedInstanceState )
		{
			requestWindowFeature( Window.FEATURE_NO_TITLE );

			super.onCreate( savedInstanceState );

			setPreferenceScreen( getPreferenceManager( ).createPreferenceScreen( this ) );

			PreferenceCategory pc = new PreferenceCategory( this );
			pc.setTitle( R.string.preference );
			getPreferenceScreen( ).addPreference( pc );

			Preference perfInterval = new Preference( this );
			perfInterval.setKey( PREF_KEY_REFRESH_INTERVAL );
			perfInterval.setTitle( R.string.update_speed );
			pc.addPreference( perfInterval );

			Preference perfRemote = new Preference( this );
			perfRemote.setKey( PREF_KEY_REMOTE_QUERY );
			perfRemote.setTitle( R.string.remote_query );
			pc.addPreference( perfRemote );

			refreshInterval( );
			refreshRemoteQuery( );

			setResult( RESULT_OK, getIntent( ) );
		}

		private void refreshInterval( )
		{
			int interval = getIntent( ).getIntExtra( PREF_KEY_REFRESH_INTERVAL,
					REFRESH_NORMAL );

			CharSequence label = getString( R.string.normal );
			switch ( interval )
			{
				case REFRESH_HIGH :
					label = getString( R.string.high );
					break;
				case REFRESH_LOW :
					label = getString( R.string.low );
					break;
				case REFRESH_PAUSED :
					label = getString( R.string.paused );
					break;
			}

			findPreference( PREF_KEY_REFRESH_INTERVAL ).setSummary( label );
		}

		private void refreshRemoteQuery( )
		{
			int state = getIntent( ).getIntExtra( PREF_KEY_REMOTE_QUERY,
					ENABLED );

			CharSequence label = getString( R.string.wifi_only );
			switch ( state )
			{
				case DISABLED :
					label = getString( R.string.disabled );
					break;
				case ENABLED :
					label = getString( R.string.enabled );
					break;
			}

			findPreference( PREF_KEY_REMOTE_QUERY ).setSummary( label );
		}

		@Override
		public boolean onPreferenceTreeClick(
				PreferenceScreen preferenceScreen, Preference preference )
		{
			final Intent it = getIntent( );

			if ( PREF_KEY_REFRESH_INTERVAL.equals( preference.getKey( ) ) )
			{
				OnClickListener listener = new OnClickListener( ) {

					public void onClick( DialogInterface dialog, int which )
					{
						it.putExtra( PREF_KEY_REFRESH_INTERVAL, which );

						dialog.dismiss( );

						refreshInterval( );
					}
				};

				new AlertDialog.Builder( this ).setTitle( R.string.update_speed )
						.setNeutralButton( R.string.close, null )
						.setSingleChoiceItems( new CharSequence[]{
								getString( R.string.high ),
								getString( R.string.normal ),
								getString( R.string.low ),
								getString( R.string.paused ),
						},
								it.getIntExtra( PREF_KEY_REFRESH_INTERVAL,
										REFRESH_NORMAL ),
								listener )
						.create( )
						.show( );

				return true;
			}
			else if ( PREF_KEY_REMOTE_QUERY.equals( preference.getKey( ) ) )
			{
				OnClickListener listener = new OnClickListener( ) {

					public void onClick( DialogInterface dialog, int which )
					{
						it.putExtra( PREF_KEY_REMOTE_QUERY, which );

						dialog.dismiss( );

						refreshRemoteQuery( );
					}
				};

				new AlertDialog.Builder( this ).setTitle( R.string.remote_query )
						.setNeutralButton( R.string.close, null )
						.setSingleChoiceItems( new CharSequence[]{
								getString( R.string.enabled ),
								getString( R.string.disabled ),
								getString( R.string.wifi_only ),
						},
								it.getIntExtra( PREF_KEY_REMOTE_QUERY, ENABLED ),
								listener )
						.create( )
						.show( );

				return true;
			}

			return false;
		}
	}

	/**
	 * ConnectionItem
	 */
	private static final class ConnectionItem
	{

		String proto;
		String ip;
		String remote;
		String state;

	}

	/**
	 * IpInfo
	 */
	static final class IpInfo
	{

		String country, region, city;
		String latitude, longitude;
		String ip, host;
	}
}
