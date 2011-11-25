/********************************************************************************
 * (C) Copyright 2000-2010, by Shawn Qualia.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 ********************************************************************************/

package org.uguess.android.sysinfo;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.net.URL;
import java.text.Collator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserFactory;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ListActivity;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.preference.CheckBoxPreference;
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
import android.view.ContextMenu.ContextMenuInfo;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.widget.AdapterView;
import android.widget.AdapterView.AdapterContextMenuInfo;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ArrayAdapter;
import android.widget.TextView;

/**
 * NetStateManager
 */
public final class NetStateManager extends ListActivity implements Constants
{

	private static final String PREF_KEY_REMOTE_QUERY = "remote_query"; //$NON-NLS-1$
	private static final String PREF_KEY_SHOW_REMOTE_NAME = "show_remote_name"; //$NON-NLS-1$
	private static final String PREF_KEY_SHOW_LOCAL_ADDRESS = "show_local_address"; //$NON-NLS-1$

	private static final int ORDER_TYPE_PROTO = 0;
	private static final int ORDER_TYPE_LOCAL = 1;
	private static final int ORDER_TYPE_REMOTE = 2;
	private static final int ORDER_TYPE_STATE = 3;

	private static final int ENABLED = 0;
	private static final int DISABLED = 1;
	private static final int WIFI_ONLY = 2;

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

	ConnectionItem dummyInfo;

	HashMap<String, IpInfo> queryCache;

	ProgressDialog progress;

	volatile boolean aborted;

	Handler handler = new Handler( ) {

		public void handleMessage( android.os.Message msg )
		{
			switch ( msg.what )
			{
				case MSG_CONTENT_READY :

					if ( aborted )
					{
						return;
					}

					showIpInfo( (IpInfo) msg.obj, NetStateManager.this );

					this.post( task );

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

	Runnable task = new Runnable( ) {

		public void run( )
		{
			refresh( );

			int interval = Util.getIntOption( NetStateManager.this,
					PREF_KEY_REFRESH_INTERVAL,
					REFRESH_LOW );

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
		dummyInfo.local = getString( R.string.local_remote_addr );
		dummyInfo.state = getString( R.string.state );

		registerForContextMenu( getListView( ) );

		getListView( ).setOnItemClickListener( new OnItemClickListener( ) {

			public void onItemClick( AdapterView<?> parent, View view,
					int position, long id )
			{
				if ( position > 0 )
				{
					int state = Util.getIntOption( NetStateManager.this,
							PREF_KEY_REMOTE_QUERY,
							ENABLED );

					if ( state == DISABLED )
					{
						return;
					}
					else if ( state == WIFI_ONLY )
					{
						ConnectivityManager cm = (ConnectivityManager) getSystemService( Context.CONNECTIVITY_SERVICE );

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
						Util.shortToast( NetStateManager.this,
								R.string.no_ip_info );
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

				if ( position >= getCount( ) )
				{
					return view;
				}

				ConnectionItem itm = getItem( position );

				txt_proto = (TextView) view.findViewById( R.id.txt_proto );
				txt_ip = (TextView) view.findViewById( R.id.txt_ip );
				txt_state = (TextView) view.findViewById( R.id.txt_state );

				txt_proto.setText( itm.proto );
				txt_state.setText( itm.state );

				boolean showLocal = Util.getBooleanOption( NetStateManager.this,
						PREF_KEY_SHOW_LOCAL_ADDRESS );

				if ( itm == dummyInfo )
				{
					if ( showLocal )
					{
						txt_ip.setText( R.string.local_remote_addr );
					}
					else
					{
						txt_ip.setText( R.string.remote_address );
					}

					txt_proto.setTextColor( Color.WHITE );
					txt_ip.setTextColor( Color.WHITE );
					txt_state.setTextColor( Color.WHITE );
				}
				else
				{
					if ( showLocal )
					{
						txt_ip.setText( itm.local
								+ '\n'
								+ ( itm.remoteName == null ? itm.remote
										: itm.remoteName ) );
					}
					else
					{
						txt_ip.setText( itm.remoteName == null ? itm.remote
								: itm.remoteName );
					}

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
	protected void onDestroy( )
	{
		if ( progress != null )
		{
			progress.dismiss( );
			progress = null;
		}

		( (ArrayAdapter<ConnectionItem>) getListView( ).getAdapter( ) ).clear( );

		super.onDestroy( );
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
		handler.removeMessages( MSG_CONTENT_READY );

		super.onPause( );
	}

	@Override
	public boolean onCreateOptionsMenu( Menu menu )
	{
		MenuItem mi = menu.add( Menu.NONE,
				MI_PREFERENCE,
				Menu.NONE,
				R.string.preference );
		mi.setIcon( android.R.drawable.ic_menu_preferences );

		return true;
	}

	@Override
	public boolean onOptionsItemSelected( MenuItem item )
	{
		if ( item.getItemId( ) == MI_PREFERENCE )
		{
			Intent it = new Intent( this, NetStateSettings.class );

			it.putExtra( PREF_KEY_REFRESH_INTERVAL, Util.getIntOption( this,
					PREF_KEY_REFRESH_INTERVAL,
					REFRESH_LOW ) );
			it.putExtra( PREF_KEY_REMOTE_QUERY,
					Util.getIntOption( this, PREF_KEY_REMOTE_QUERY, ENABLED ) );
			it.putExtra( PREF_KEY_SHOW_REMOTE_NAME,
					Util.getBooleanOption( this, PREF_KEY_SHOW_REMOTE_NAME ) );
			it.putExtra( PREF_KEY_SHOW_LOCAL_ADDRESS,
					Util.getBooleanOption( this, PREF_KEY_SHOW_LOCAL_ADDRESS ) );
			it.putExtra( PREF_KEY_SORT_ORDER_TYPE, Util.getIntOption( this,
					PREF_KEY_SORT_ORDER_TYPE,
					ORDER_TYPE_PROTO ) );
			it.putExtra( PREF_KEY_SORT_DIRECTION,
					Util.getIntOption( this, PREF_KEY_SORT_DIRECTION, ORDER_ASC ) );

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

		if ( pos > 0 && pos < getListView( ).getCount( ) )
		{
			ConnectionItem itm = (ConnectionItem) getListView( ).getItemAtPosition( pos );

			if ( itm != null && !TextUtils.isEmpty( itm.remote ) )
			{
				ClipboardManager cm = (ClipboardManager) getSystemService( CLIPBOARD_SERVICE );

				if ( cm != null )
				{
					cm.setText( itm.remoteName == null ? itm.remote
							: itm.remoteName );

					Util.shortToast( this, R.string.copied_hint );
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
		if ( requestCode == 1 && data != null )
		{
			Util.updateIntOption( data,
					this,
					PREF_KEY_REFRESH_INTERVAL,
					REFRESH_LOW );
			Util.updateIntOption( data, this, PREF_KEY_REMOTE_QUERY, ENABLED );
			Util.updateIntOption( data,
					this,
					PREF_KEY_SORT_ORDER_TYPE,
					ORDER_TYPE_PROTO );
			Util.updateIntOption( data,
					this,
					PREF_KEY_SORT_DIRECTION,
					ORDER_ASC );
			Util.updateBooleanOption( data, this, PREF_KEY_SHOW_REMOTE_NAME );
			Util.updateBooleanOption( data, this, PREF_KEY_SHOW_LOCAL_ADDRESS );
		}
	}

	static String getValidIP( String ip )
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

	void queryIPInfo( final String ip )
	{
		IpInfo info = queryCache.get( ip );

		if ( info != null )
		{
			handler.sendMessage( handler.obtainMessage( MSG_CONTENT_READY, info ) );
			return;
		}

		if ( progress != null )
		{
			progress.dismiss( );
		}
		progress = new ProgressDialog( this );
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

				handler.sendMessage( handler.obtainMessage( MSG_CONTENT_READY,
						info ) );
			}
		} ).start( );
	}

	void refresh( )
	{
		ArrayList<ConnectionItem> data = new ArrayList<ConnectionItem>( );

		data.add( dummyInfo );

		ArrayList<ConnectionItem> items = readStatesRaw( );

		if ( items != null )
		{
			final int type = Util.getIntOption( this,
					PREF_KEY_SORT_ORDER_TYPE,
					ORDER_TYPE_PROTO );
			final int direction = Util.getIntOption( this,
					PREF_KEY_SORT_DIRECTION,
					ORDER_ASC );
			final Collator clt = Collator.getInstance( );

			switch ( type )
			{
				case ORDER_TYPE_PROTO :
					Collections.sort( items, new Comparator<ConnectionItem>( ) {

						public int compare( ConnectionItem obj1,
								ConnectionItem obj2 )
						{
							return clt.compare( obj1.proto, obj2.proto )
									* direction;
						}
					} );
					break;
				case ORDER_TYPE_LOCAL :
					Collections.sort( items, new Comparator<ConnectionItem>( ) {

						public int compare( ConnectionItem obj1,
								ConnectionItem obj2 )
						{
							return clt.compare( obj1.local, obj2.local )
									* direction;
						}
					} );
					break;
				case ORDER_TYPE_REMOTE :
					Collections.sort( items, new Comparator<ConnectionItem>( ) {

						public int compare( ConnectionItem obj1,
								ConnectionItem obj2 )
						{
							return clt.compare( obj1.remoteName == null ? obj1.remote
									: obj1.remoteName,
									obj2.remoteName == null ? obj2.remote
											: obj2.remoteName )
									* direction;
						}
					} );
					break;
				case ORDER_TYPE_STATE :
					Collections.sort( items, new Comparator<ConnectionItem>( ) {

						public int compare( ConnectionItem obj1,
								ConnectionItem obj2 )
						{
							return clt.compare( obj1.state == null ? "" //$NON-NLS-1$
									: obj1.state, obj2.state == null ? "" //$NON-NLS-1$
									: obj2.state ) * direction;
						}
					} );
					break;
			}

			data.addAll( items );
		}

		ArrayAdapter<ConnectionItem> adapter = (ArrayAdapter<ConnectionItem>) getListView( ).getAdapter( );

		adapter.setNotifyOnChange( false );

		adapter.clear( );

		for ( int i = 0, size = data.size( ); i < size; i++ )
		{
			adapter.add( data.get( i ) );
		}

		adapter.notifyDataSetChanged( );

		if ( adapter.getCount( ) == 1 )
		{
			Log.d( NetStateManager.class.getName( ),
					"No network traffic detected" ); //$NON-NLS-1$
		}
	}

	void setFont( TextView txt, int type )
	{
		if ( txt.getTypeface( ) == null
				|| txt.getTypeface( ).getStyle( ) != type )
		{
			txt.setTypeface( Typeface.DEFAULT, type );
		}
	}

	private ArrayList<ConnectionItem> readStatesRaw( )
	{
		ArrayList<ConnectionItem> items = new ArrayList<NetStateManager.ConnectionItem>( );

		parseRawData( items, this, queryCache, "TCP", "/proc/net/tcp", false ); //$NON-NLS-1$ //$NON-NLS-2$
		parseRawData( items, this, queryCache, "UDP", "/proc/net/udp", true ); //$NON-NLS-1$ //$NON-NLS-2$
		parseRawData( items, this, queryCache, "TCP", "/proc/net/tcp6", false ); //$NON-NLS-1$ //$NON-NLS-2$
		parseRawData( items, this, queryCache, "UDP", "/proc/net/udp6", true ); //$NON-NLS-1$ //$NON-NLS-2$

		return items;
	}

	private static void parseRawData( final ArrayList<ConnectionItem> items,
			Activity ac, HashMap<String, IpInfo> queryCache, String proto,
			String source, boolean ignoreState )
	{
		BufferedReader reader = null;
		try
		{
			reader = new BufferedReader( new InputStreamReader( new FileInputStream( source ) ),
					4096 );

			boolean first = true;
			int localOffset = -1, remOffset = -1, stateOffset = -1, stateEndOffset = -1;
			String line;

			final boolean showRemoteName = Util.getBooleanOption( ac,
					PREF_KEY_SHOW_REMOTE_NAME );
			String remoteIp;
			int portIdx;
			IpInfo remoteInfo;

			while ( ( line = reader.readLine( ) ) != null )
			{
				if ( first )
				{
					localOffset = line.indexOf( "local_address" ); //$NON-NLS-1$
					remOffset = line.indexOf( "rem_address" ); //$NON-NLS-1$
					if ( remOffset == -1 )
					{
						remOffset = line.indexOf( "remote_address" ); //$NON-NLS-1$
					}
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

					ci.local = parseRawIP( line.substring( localOffset,
							remOffset ).trim( ) );

					ci.remote = parseRawIP( line.substring( remOffset,
							stateOffset ).trim( ) );

					if ( showRemoteName )
					{
						remoteIp = getValidIP( ci.remote );

						if ( remoteIp != null )
						{
							remoteInfo = queryCache.get( remoteIp );

							if ( remoteInfo != null
									&& !TextUtils.isEmpty( remoteInfo.host ) )
							{
								portIdx = ci.remote.lastIndexOf( ':' );

								if ( portIdx != -1 )
								{
									ci.remoteName = remoteInfo.host
											+ ci.remote.substring( portIdx );
								}
								else
								{
									ci.remoteName = remoteInfo.host;
								}
							}
						}
					}

					if ( !ignoreState )
					{
						int st = Integer.parseInt( line.substring( stateOffset,
								stateEndOffset ).trim( ),
								16 );

						ci.state = "Unknown"; //$NON-NLS-1$

						if ( st > 0 && st <= SOCKET_STATES.length )
						{
							ci.state = SOCKET_STATES[st - 1];
						}
					}

					items.add( ci );
				}
			}
		}
		catch ( FileNotFoundException fe )
		{
			Log.d( NetStateManager.class.getName( ),
					"File not found: " + fe.getLocalizedMessage( ) ); //$NON-NLS-1$
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
	}

	private static String parseRawIP( String raw )
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

				if ( ip.length( ) == 8 )
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
								"Parsing raw ip4 fail : " + raw ); //$NON-NLS-1$
					}
				}
				else if ( ip.length( ) == 32 )
				{
					try
					{
						int n1 = Integer.parseInt( ip.substring( 30 ), 16 );
						int n2 = Integer.parseInt( ip.substring( 28, 30 ), 16 );
						int n3 = Integer.parseInt( ip.substring( 26, 28 ), 16 );
						int n4 = Integer.parseInt( ip.substring( 24, 26 ), 16 );

						ip = n1 + "." + n2 + "." + n3 + "." + n4; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
					}
					catch ( Exception e )
					{
						ip = "?"; //$NON-NLS-1$

						Log.e( NetStateManager.class.getName( ),
								"Parsing raw ip6 fail : " + raw ); //$NON-NLS-1$
					}
				}
				else
				{
					Log.e( NetStateManager.class.getName( ),
							"Parsing raw ip fail : " + raw ); //$NON-NLS-1$
				}

				return ip + ':' + port;
			}
		}

		return raw;
	}

	static void showIpInfo( final IpInfo info, final Activity context )
	{
		if ( info != null
				&& !TextUtils.isEmpty( info.latitude )
				&& !TextUtils.isEmpty( info.longitude ) )
		{

			OnClickListener listener = new OnClickListener( ) {

				public void onClick( DialogInterface dialog, int which )
				{
					Intent it = new Intent( Intent.ACTION_VIEW );

					it.setData( Uri.parse( "geo:0,0?q=" //$NON-NLS-1$
							+ info.latitude
							+ "," //$NON-NLS-1$
							+ info.longitude
							+ "&z=8" ) ); //$NON-NLS-1$

					it = Intent.createChooser( it, null );

					context.startActivity( it );
				}
			};

			TextView txt = new TextView( context );
			txt.setPadding( 15, 0, 15, 0 );
			txt.setTextAppearance( context,
					android.R.style.TextAppearance_Medium );

			txt.setText( Html.fromHtml( context.getString( R.string.location_info,
					info.ip,
					info.host == null ? "" : ( "<a href=\"http://" //$NON-NLS-1$ //$NON-NLS-2$
							+ info.host
							+ "\">" //$NON-NLS-1$
							+ info.host + "</a><br>" ), //$NON-NLS-1$
					info.country == null ? "" : info.country, //$NON-NLS-1$
					info.region == null ? "" : info.region, //$NON-NLS-1$
					info.city == null ? "" : info.city ) ) ); //$NON-NLS-1$
			txt.setMovementMethod( LinkMovementMethod.getInstance( ) );

			new AlertDialog.Builder( context ).setTitle( R.string.ip_location )
					.setPositiveButton( R.string.view_map, listener )
					.setNegativeButton( R.string.close, null )
					.setView( txt )
					.create( )
					.show( );
		}
		else
		{
			Util.shortToast( context, R.string.no_ip_info );
		}
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
				url = new URL( "http://api.ipinfodb.com/v2/ip_query.php?key=a1d74831f68f12aa61307b387b0d17cf2501d9c368172a9c73ad120f149c73d4" ); //$NON-NLS-1$
			}
			else
			{
				url = new URL( "http://api.ipinfodb.com/v2/ip_query.php?key=a1d74831f68f12aa61307b387b0d17cf2501d9c368172a9c73ad120f149c73d4&ip=" //$NON-NLS-1$
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
									"Status returned: [" //$NON-NLS-1$
											+ value
											+ "] for ip: " //$NON-NLS-1$
											+ info.ip );

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

			CheckBoxPreference perfRemoteName = new CheckBoxPreference( this );
			perfRemoteName.setKey( PREF_KEY_SHOW_REMOTE_NAME );
			perfRemoteName.setTitle( R.string.display_remote_name );
			perfRemoteName.setSummary( R.string.show_remote_msg );
			pc.addPreference( perfRemoteName );

			CheckBoxPreference perfShowLocal = new CheckBoxPreference( this );
			perfShowLocal.setKey( PREF_KEY_SHOW_LOCAL_ADDRESS );
			perfShowLocal.setTitle( R.string.show_local_addr );
			perfShowLocal.setSummary( R.string.show_local_addr_sum );
			pc.addPreference( perfShowLocal );

			pc = new PreferenceCategory( this );
			pc.setTitle( R.string.sort );
			getPreferenceScreen( ).addPreference( pc );

			Preference perfSortType = new Preference( this );
			perfSortType.setKey( PREF_KEY_SORT_ORDER_TYPE );
			perfSortType.setTitle( R.string.sort_type );
			pc.addPreference( perfSortType );

			Preference perfSortDirection = new Preference( this );
			perfSortDirection.setKey( PREF_KEY_SORT_DIRECTION );
			perfSortDirection.setTitle( R.string.sort_direction );
			pc.addPreference( perfSortDirection );

			refreshInterval( );
			refreshRemoteQuery( );
			refreshBooleanOption( PREF_KEY_SHOW_REMOTE_NAME );
			refreshBooleanOption( PREF_KEY_SHOW_LOCAL_ADDRESS );
			refreshSortType( );
			refreshSortDirection( );

			setResult( RESULT_OK, getIntent( ) );
		}

		void refreshInterval( )
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

		void refreshRemoteQuery( )
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

		void refreshBooleanOption( String key )
		{
			boolean val = getIntent( ).getBooleanExtra( key, true );

			( (CheckBoxPreference) findPreference( key ) ).setChecked( val );
		}

		void refreshSortType( )
		{
			int type = getIntent( ).getIntExtra( PREF_KEY_SORT_ORDER_TYPE,
					ORDER_TYPE_PROTO );

			String label = null;
			switch ( type )
			{
				case ORDER_TYPE_PROTO :
					label = getString( R.string.protocol );
					break;
				case ORDER_TYPE_LOCAL :
					label = getString( R.string.local_address );
					break;
				case ORDER_TYPE_REMOTE :
					label = getString( R.string.remote_address );
					break;
				case ORDER_TYPE_STATE :
					label = getString( R.string.state );
					break;
			}

			findPreference( PREF_KEY_SORT_ORDER_TYPE ).setSummary( label );
		}

		void refreshSortDirection( )
		{
			int type = getIntent( ).getIntExtra( PREF_KEY_SORT_DIRECTION,
					ORDER_ASC );

			String label = type == ORDER_ASC ? getString( R.string.ascending )
					: getString( R.string.descending );

			findPreference( PREF_KEY_SORT_DIRECTION ).setSummary( label );
		}

		@Override
		public boolean onPreferenceTreeClick(
				PreferenceScreen preferenceScreen, Preference preference )
		{
			final Intent it = getIntent( );

			final String prefKey = preference.getKey( );

			if ( PREF_KEY_REFRESH_INTERVAL.equals( prefKey ) )
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
			else if ( PREF_KEY_REMOTE_QUERY.equals( prefKey ) )
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
			else if ( PREF_KEY_SHOW_REMOTE_NAME.equals( prefKey ) )
			{
				it.putExtra( PREF_KEY_SHOW_REMOTE_NAME,
						( (CheckBoxPreference) findPreference( PREF_KEY_SHOW_REMOTE_NAME ) ).isChecked( ) );

				return true;
			}
			else if ( PREF_KEY_SHOW_LOCAL_ADDRESS.equals( prefKey ) )
			{
				it.putExtra( PREF_KEY_SHOW_LOCAL_ADDRESS,
						( (CheckBoxPreference) findPreference( PREF_KEY_SHOW_LOCAL_ADDRESS ) ).isChecked( ) );

				return true;
			}
			else if ( PREF_KEY_SORT_ORDER_TYPE.equals( prefKey ) )
			{
				OnClickListener listener = new OnClickListener( ) {

					public void onClick( DialogInterface dialog, int which )
					{
						it.putExtra( PREF_KEY_SORT_ORDER_TYPE, which );

						dialog.dismiss( );

						refreshSortType( );
					}
				};

				new AlertDialog.Builder( this ).setTitle( R.string.sort_type )
						.setNeutralButton( R.string.close, null )
						.setSingleChoiceItems( new String[]{
								getString( R.string.protocol ),
								getString( R.string.local_address ),
								getString( R.string.remote_address ),
								getString( R.string.state ),
						},
								it.getIntExtra( PREF_KEY_SORT_ORDER_TYPE,
										ORDER_TYPE_PROTO ),
								listener )
						.create( )
						.show( );

				return true;
			}
			else if ( PREF_KEY_SORT_DIRECTION.equals( prefKey ) )
			{
				OnClickListener listener = new OnClickListener( ) {

					public void onClick( DialogInterface dialog, int which )
					{
						it.putExtra( PREF_KEY_SORT_DIRECTION,
								which == 0 ? ORDER_ASC : ORDER_DESC );

						dialog.dismiss( );

						refreshSortDirection( );
					}
				};

				new AlertDialog.Builder( this ).setTitle( R.string.sort_direction )
						.setNeutralButton( R.string.close, null )
						.setSingleChoiceItems( new String[]{
								getString( R.string.ascending ),
								getString( R.string.descending ),
						},
								it.getIntExtra( PREF_KEY_SORT_DIRECTION,
										ORDER_ASC ) == ORDER_ASC ? 0 : 1,
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
		String local;
		String remote;
		String remoteName;
		String state;

		ConnectionItem( )
		{

		}
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
