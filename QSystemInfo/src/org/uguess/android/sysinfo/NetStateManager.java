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
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserFactory;

import android.app.AlertDialog;
import android.app.ListActivity;
import android.app.ProgressDialog;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.DialogInterface.OnClickListener;
import android.content.SharedPreferences.Editor;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceCategory;
import android.preference.PreferenceScreen;
import android.text.ClipboardManager;
import android.text.TextUtils;
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

	private static final int REFRESH_HIGH = 0;
	private static final int REFRESH_NORMAL = 1;
	private static final int REFRESH_LOW = 2;
	private static final int REFRESH_PAUSED = 3;

	private static final int MSG_IP_READY = 1;
	private static final int MSG_DISMISS_PROGRESS = 2;

	private ConnectionItem dummyInfo;

	private HashMap<String, IpInfo> queryCache;

	private ProgressDialog progress;

	private Handler handler = new Handler( ) {

		public void handleMessage( android.os.Message msg )
		{
			switch ( msg.what )
			{
				case MSG_IP_READY :

					sendEmptyMessage( MSG_DISMISS_PROGRESS );

					IpInfo info = (IpInfo) msg.obj;

					if ( info != null
							&& !TextUtils.isEmpty( info.longitude )
							&& !TextUtils.isEmpty( info.latitude ) )
					{
						Intent it = new Intent( Intent.ACTION_VIEW );

						it.setData( Uri.parse( "geo:0,0?q=" //$NON-NLS-1$
								+ info.latitude
								+ "," //$NON-NLS-1$
								+ info.longitude
								+ "&z=8" ) ); //$NON-NLS-1$

						try
						{
							startActivity( it );
						}
						catch ( ActivityNotFoundException e )
						{
							Log.w( NetStateManager.class.getName( ),
									"No activity found to handle uri: " //$NON-NLS-1$
											+ it.getData( ) );

							new AlertDialog.Builder( NetStateManager.this ).setTitle( R.string.ip_location )
									.setNeutralButton( R.string.close, null )
									.setMessage( getString( R.string.location_info,
											info.country,
											info.region,
											info.city ) )
									.create( )
									.show( );
						}
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
		super.onResume( );

		handler.post( task );
	}

	@Override
	protected void onPause( )
	{
		handler.removeCallbacks( task );

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
				InputStream input = null;
				IpInfo info = null;
				try
				{
					URL url = new URL( "http://ipinfodb.com/ip_query.php?ip=" //$NON-NLS-1$
							+ ip );

					XmlPullParser parser = XmlPullParserFactory.newInstance( )
							.newPullParser( );

					input = url.openStream( );

					parser.setInput( input, null );

					info = new IpInfo( );

					String name, value;
					while ( parser.next( ) != XmlPullParser.END_DOCUMENT )
					{
						if ( parser.getEventType( ) == XmlPullParser.START_TAG )
						{
							name = parser.getName( );

							if ( "Status".equals( name ) ) //$NON-NLS-1$
							{
								value = parser.nextText( );

								if ( !"OK".equals( value ) ) //$NON-NLS-1$
								{
									break;
								}
							}
							else if ( "CountryName".equals( name ) ) //$NON-NLS-1$
							{
								value = parser.nextText( );

								if ( "Reserved".equals( value ) ) //$NON-NLS-1$
								{
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

					queryCache.put( ip, info );

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

					handler.sendMessage( handler.obtainMessage( MSG_IP_READY,
							info ) );
				}

			}
		} ).start( );
	}

	private void refresh( )
	{
		ArrayList<ConnectionItem> data = new ArrayList<ConnectionItem>( );

		data.add( dummyInfo );

		ArrayList<ConnectionItem> items = readStates( );

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
	}

	private void setFont( TextView txt, int type )
	{
		if ( txt.getTypeface( ) == null
				|| txt.getTypeface( ).getStyle( ) != type )
		{
			txt.setTypeface( Typeface.DEFAULT, type );
		}
	}

	private ArrayList<ConnectionItem> readStates( )
	{
		BufferedReader reader = null;
		try
		{
			Process proc = Runtime.getRuntime( ).exec( "netstat" ); //$NON-NLS-1$

			reader = new BufferedReader( new InputStreamReader( proc.getInputStream( ) ),
					8192 );

			ArrayList<ConnectionItem> itms = new ArrayList<ConnectionItem>( );
			boolean first = true;
			int protoOffset = -1, localOffset = -1, foreignOffset = -1, stateOffset = -1;
			String line;

			while ( ( line = reader.readLine( ) ) != null )
			{
				if ( first )
				{
					if ( !line.startsWith( "Proto" ) ) //$NON-NLS-1$
					{
						break;
					}

					protoOffset = line.indexOf( "Recv-Q" ); //$NON-NLS-1$
					localOffset = line.indexOf( "Local Address" ); //$NON-NLS-1$
					foreignOffset = line.indexOf( "Foreign Address" ); //$NON-NLS-1$
					stateOffset = line.indexOf( "State" ); //$NON-NLS-1$

					if ( protoOffset == -1
							|| localOffset == -1
							|| foreignOffset == -1
							|| stateOffset == -1 )
					{
						break;
					}

					first = false;
				}
				else
				{
					ConnectionItem ci = new ConnectionItem( );

					ci.proto = line.substring( 0, protoOffset )
							.trim( )
							.toUpperCase( );

					if ( stateOffset < line.length( ) )
					{
						ci.state = line.substring( stateOffset ).trim( );
						ci.remote = line.substring( foreignOffset, stateOffset )
								.trim( );
					}
					else
					{
						ci.remote = line.substring( foreignOffset ).trim( );
					}

					String local = line.substring( localOffset, foreignOffset )
							.trim( );

					ci.ip = local + '\n' + ci.remote;

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

			refreshInterval( );

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
	private static final class IpInfo
	{

		String country, region, city;
		String latitude, longitude;
	}
}
