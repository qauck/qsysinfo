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
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Date;
import java.util.regex.Pattern;

import org.uguess.android.sysinfo.SysInfoManager.FormatArrayAdapter;
import org.uguess.android.sysinfo.SysInfoManager.FormatItem;

import android.app.AlertDialog;
import android.app.ListActivity;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceCategory;
import android.preference.PreferenceScreen;
import android.text.ClipboardManager;
import android.text.Html;
import android.text.InputFilter;
import android.text.TextUtils;
import android.text.method.DigitsKeyListener;
import android.util.Log;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.AdapterView.AdapterContextMenuInfo;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ListAdapter;
import android.widget.TextView;

/**
 * LogViewer
 */
public final class LogViewer extends ListActivity implements Constants
{

	private static final Pattern DMESG_TIME_PATTERN = Pattern.compile( "\\d+\\.\\d+" ); //$NON-NLS-1$

	private static final String PREF_KEY_CLOG_LEVL = "clog_level"; //$NON-NLS-1$
	private static final String PREF_KEY_DLOG_LEVL = "dlog_level"; //$NON-NLS-1$
	private static final String PREF_KEY_RING_BUFFER = "ring_buffer"; //$NON-NLS-1$
	private static final String PREF_KEY_TAG_FILTER = "tag_filter"; //$NON-NLS-1$
	private static final String PREF_KEY_PID_FILTER = "pid_filter"; //$NON-NLS-1$

	private static final int DM_LVL_EMMERGENCY = 0;
	private static final int DM_LVL_ALERT = 1;
	private static final int DM_LVL_CRITICAL = 2;
	private static final int DM_LVL_ERROR = 3;
	private static final int DM_LVL_WARNING = 4;
	private static final int DM_LVL_NOTICE = 5;
	private static final int DM_LVL_INFORMATION = 6;
	private static final int DM_LVL_DEBUG = 7;

	private static final int RING_BUFFER_MAIN = 0;
	private static final int RING_BUFFER_RADIO = 1;
	private static final int RING_BUFFER_EVENTS = 2;

	static final String DMESG_MODE = "dmesgMode"; //$NON-NLS-1$

	boolean dmesgMode;

	ProgressDialog progress;

	volatile boolean aborted;

	Handler handler = new Handler( ) {

		public void handleMessage( android.os.Message msg )
		{
			switch ( msg.what )
			{
				case MSG_INIT_OK :

					ArrayAdapter<LogItem> adapter = (ArrayAdapter<LogItem>) getListView( ).getAdapter( );

					adapter.setNotifyOnChange( false );

					adapter.clear( );

					ArrayList<LogItem> data = (ArrayList<LogItem>) msg.obj;

					if ( data != null )
					{
						for ( int i = 0, size = data.size( ); i < size; i++ )
						{
							adapter.add( data.get( i ) );
						}
					}

					adapter.notifyDataSetChanged( );

					sendEmptyMessage( MSG_DISMISS_PROGRESS );

					if ( adapter.getCount( ) == 0 )
					{
						Util.shortToast( LogViewer.this, R.string.no_log_info );
					}
					else
					{
						getListView( ).setSelection( adapter.getCount( ) - 1 );
					}

					break;
				case MSG_CONTENT_READY :

					sendEmptyMessage( MSG_DISMISS_PROGRESS );

					Util.handleMsgSendContentReady( (String) msg.obj,
							"Android Device Log - ", //$NON-NLS-1$
							LogViewer.this,
							msg.arg2 == 1 );

					break;
				case MSG_CHECK_FORCE_COMPRESSION :

					sendEmptyMessage( MSG_DISMISS_PROGRESS );

					Util.checkForceCompression( this,
							LogViewer.this,
							(String) msg.obj,
							msg.arg1,
							"android_log" ); //$NON-NLS-1$

					break;
				case MSG_DISMISS_PROGRESS :

					if ( progress != null )
					{
						progress.dismiss( );
						progress = null;
					}
					break;
				case MSG_TOAST :

					Util.shortToast( LogViewer.this, (String) msg.obj );
					break;
			}
		};
	};

	@Override
	protected void onCreate( Bundle savedInstanceState )
	{
		super.onCreate( savedInstanceState );

		dmesgMode = getIntent( ).getBooleanExtra( DMESG_MODE, false );

		getListView( ).setFastScrollEnabled( true );

		registerForContextMenu( getListView( ) );

		ArrayAdapter<LogItem> adapter = new ArrayAdapter<LogItem>( this,
				R.layout.log_item ) {

			@Override
			public View getView( int position, View convertView,
					ViewGroup parent )
			{
				View view;
				TextView txt_head, txt_msg;

				if ( convertView == null )
				{
					view = getLayoutInflater( ).inflate( R.layout.log_item,
							parent,
							false );
				}
				else
				{
					view = convertView;
				}

				if ( position >= getCount( ) )
				{
					return view;
				}

				LogItem itm = getItem( position );

				txt_msg = (TextView) view.findViewById( R.id.txt_msg );
				txt_msg.setText( itm.getMsg( ) );

				txt_head = (TextView) view.findViewById( R.id.txt_head );

				if ( dmesgMode )
				{
					txt_head.setText( formatDLog( itm ) );

					switch ( itm.level )
					{
						case '0' :
							txt_head.setTextColor( Color.MAGENTA );
							break;
						case '1' :
							txt_head.setTextColor( 0xFFF183BD );
							break;
						case '2' :
							txt_head.setTextColor( 0xFF8737CE );
							break;
						case '3' :
							txt_head.setTextColor( Color.RED );
							break;
						case '4' :
							txt_head.setTextColor( Color.YELLOW );
							break;
						case '5' :
							txt_head.setTextColor( Color.CYAN );
							break;
						case '6' :
							txt_head.setTextColor( Color.GREEN );
							break;
						case '7' :
						default :
							txt_head.setTextColor( Color.GRAY );
							break;
					}
				}
				else
				{
					txt_head.setText( formatCLog( itm ) );

					switch ( itm.level )
					{
						case 'E' :
							txt_head.setTextColor( Color.RED );
							break;
						case 'W' :
							txt_head.setTextColor( Color.YELLOW );
							break;
						case 'I' :
							txt_head.setTextColor( Color.GREEN );
							break;
						case 'D' :
							txt_head.setTextColor( Color.CYAN );
							break;
						case 'A' :
							txt_head.setTextColor( Color.MAGENTA );
							break;
						case 'V' :
						default :
							txt_head.setTextColor( Color.GRAY );
							break;
					}
				}

				return view;
			}
		};

		getListView( ).setAdapter( adapter );

		refreshLogs( );
	}

	@Override
	protected void onDestroy( )
	{
		if ( progress != null )
		{
			progress.dismiss( );
			progress = null;
		}
		super.onDestroy( );
	}

	@Override
	protected void onResume( )
	{
		aborted = false;

		super.onResume( );
	}

	@Override
	protected void onPause( )
	{
		aborted = true;

		handler.removeMessages( MSG_CHECK_FORCE_COMPRESSION );
		handler.removeMessages( MSG_CONTENT_READY );

		super.onPause( );
	}

	@Override
	public void onCreateContextMenu( ContextMenu menu, View v,
			ContextMenuInfo menuInfo )
	{
		menu.setHeaderTitle( R.string.actions );
		menu.add( R.string.copy_text );
	}

	@Override
	public boolean onContextItemSelected( MenuItem item )
	{
		int pos = ( (AdapterContextMenuInfo) item.getMenuInfo( ) ).position;
		LogItem log = (LogItem) getListView( ).getItemAtPosition( pos );

		if ( log != null && log.getMsg( ) != null )
		{
			ClipboardManager cm = (ClipboardManager) getSystemService( CLIPBOARD_SERVICE );

			if ( cm != null )
			{
				cm.setText( log.getMsg( ) );

				Util.shortToast( this, R.string.copied_hint );
			}
		}

		return true;
	}

	@Override
	public boolean onCreateOptionsMenu( Menu menu )
	{
		MenuItem mi = menu.add( Menu.NONE,
				MI_SHARE,
				Menu.NONE,
				R.string.send_log );
		mi.setIcon( android.R.drawable.ic_menu_share );

		mi = menu.add( Menu.NONE, MI_REFRESH, Menu.NONE, R.string.refresh );
		mi.setIcon( android.R.drawable.ic_menu_rotate );

		mi = menu.add( Menu.NONE, MI_PREFERENCE, Menu.NONE, R.string.preference );
		mi.setIcon( android.R.drawable.ic_menu_preferences );

		return true;
	}

	@Override
	public boolean onOptionsItemSelected( MenuItem item )
	{
		if ( item.getItemId( ) == MI_PREFERENCE )
		{
			Intent it = new Intent( this, LogSettings.class );

			it.putExtra( DMESG_MODE, dmesgMode );

			it.putExtra( PREF_KEY_CLOG_LEVL,
					Util.getIntOption( this, PREF_KEY_CLOG_LEVL, Log.VERBOSE ) );
			it.putExtra( PREF_KEY_RING_BUFFER, Util.getIntOption( this,
					PREF_KEY_RING_BUFFER,
					RING_BUFFER_MAIN ) );
			it.putExtra( PREF_KEY_TAG_FILTER,
					Util.getStringOption( LogViewer.this,
							PREF_KEY_TAG_FILTER,
							null ) );
			it.putExtra( PREF_KEY_PID_FILTER,
					Util.getIntOption( this, PREF_KEY_PID_FILTER, 0 ) );
			it.putExtra( PREF_KEY_DLOG_LEVL,
					Util.getIntOption( this, PREF_KEY_DLOG_LEVL, DM_LVL_DEBUG ) );

			startActivityForResult( it, 1 );

			return true;
		}
		else if ( item.getItemId( ) == MI_REFRESH )
		{
			refreshLogs( );

			return true;
		}
		else if ( item.getItemId( ) == MI_SHARE )
		{
			final FormatArrayAdapter adapter = new FormatArrayAdapter( this,
					R.layout.send_item,
					new FormatItem[]{
							new FormatItem( getString( R.string.plain_text ) ),
							new FormatItem( getString( R.string.html ) ),
							new FormatItem( getString( R.string.csv ) ),
					} );

			OnClickListener listener = new OnClickListener( ) {

				public void onClick( DialogInterface dialog, int which )
				{
					FormatItem fi = adapter.getItem( which );

					sendLog( fi.compressed, which );
				}
			};

			new AlertDialog.Builder( this ).setTitle( R.string.send_log )
					.setAdapter( adapter, listener )
					.setInverseBackgroundForced( true )
					.create( )
					.show( );

			return true;
		}

		return false;
	}

	void sendLog( final boolean compressed, final int format )
	{
		if ( progress != null )
		{
			progress.dismiss( );
		}
		progress = new ProgressDialog( this );
		progress.setMessage( getResources( ).getText( R.string.loading ) );
		progress.setIndeterminate( true );
		progress.show( );

		new Thread( new Runnable( ) {

			public void run( )
			{
				String content = null;

				switch ( format )
				{
					case SysInfoManager.PLAINTEXT :
						content = collectTextLogContent( );
						break;
					case SysInfoManager.HTML :
						content = collectHtmlLogContent( );
						break;
					case SysInfoManager.CSV :
						content = collectCSVLogContent( );
						break;
				}

				if ( content != null && compressed )
				{
					content = Util.createCompressedContent( handler,
							LogViewer.this,
							content,
							format,
							"android_log" ); //$NON-NLS-1$
				}

				if ( aborted )
				{
					return;
				}

				if ( content != null && !compressed )
				{
					handler.sendMessage( handler.obtainMessage( MSG_CHECK_FORCE_COMPRESSION,
							format,
							compressed ? 1 : 0,
							content ) );
				}
				else
				{
					handler.sendMessage( handler.obtainMessage( MSG_CONTENT_READY,
							format,
							compressed ? 1 : 0,
							content ) );
				}
			}
		} ).start( );
	}

	@Override
	protected void onActivityResult( int requestCode, int resultCode,
			Intent data )
	{
		if ( requestCode == 1 && data != null )
		{
			boolean needRefresh = false;

			if ( Util.updateIntOption( data,
					this,
					PREF_KEY_CLOG_LEVL,
					Log.VERBOSE ) )
			{
				needRefresh = true;
			}

			if ( Util.updateIntOption( data,
					this,
					PREF_KEY_RING_BUFFER,
					RING_BUFFER_MAIN ) )
			{
				needRefresh = true;
			}

			if ( Util.updateStringOption( data, this, PREF_KEY_TAG_FILTER ) )
			{
				needRefresh = true;
			}

			if ( Util.updateIntOption( data, this, PREF_KEY_PID_FILTER, 0 ) )
			{
				needRefresh = true;
			}

			if ( Util.updateIntOption( data,
					this,
					PREF_KEY_DLOG_LEVL,
					DM_LVL_DEBUG ) )
			{
				needRefresh = true;
			}

			if ( needRefresh )
			{
				refreshLogs( );
			}
		}
	}

	String collectTextLogContent( )
	{
		StringBuffer sb = new StringBuffer( );

		SysInfoManager.createTextHeader( this, sb, "Android Device Log - " //$NON-NLS-1$
				+ new Date( ).toLocaleString( ) );

		ListAdapter adapter = getListView( ).getAdapter( );
		int cnt = adapter.getCount( );

		if ( dmesgMode )
		{
			String head;
			for ( int i = 0; i < cnt; i++ )
			{
				LogItem log = (LogItem) adapter.getItem( i );

				head = formatDLog( log ) + " :\t"; //$NON-NLS-1$

				if ( log.msgList == null )
				{
					sb.append( head ).append( log.msg ).append( '\n' );
				}
				else
				{
					for ( int k = 0, size = log.msgList.size( ); k < size; k++ )
					{
						sb.append( head )
								.append( log.msgList.get( k ) )
								.append( '\n' );
					}
				}
			}
		}
		else
		{
			String head;
			for ( int i = 0; i < cnt; i++ )
			{
				LogItem log = (LogItem) adapter.getItem( i );

				head = formatCLog( log ) + " :\t"; //$NON-NLS-1$

				if ( log.msgList == null )
				{
					sb.append( head ).append( log.msg ).append( '\n' );
				}
				else
				{
					for ( int k = 0, size = log.msgList.size( ); k < size; k++ )
					{
						sb.append( head )
								.append( log.msgList.get( k ) )
								.append( '\n' );
					}
				}
			}
		}

		return sb.toString( );
	}

	String collectCSVLogContent( )
	{
		StringBuffer sb = new StringBuffer( );

		ListAdapter adapter = getListView( ).getAdapter( );
		int cnt = adapter.getCount( );

		if ( dmesgMode )
		{
			sb.append( "LEVEL,TIME,MSG\n" ); //$NON-NLS-1$

			for ( int i = 0; i < cnt; i++ )
			{
				LogItem log = (LogItem) adapter.getItem( i );

				if ( log.msgList == null )
				{
					sb.append( log.level ).append( ',' );

					if ( log.time != null )
					{
						sb.append( SysInfoManager.escapeCsv( log.time ) );
					}

					sb.append( ',' )
							.append( SysInfoManager.escapeCsv( log.msg ) )
							.append( '\n' );
				}
				else
				{
					for ( int k = 0, size = log.msgList.size( ); k < size; k++ )
					{
						sb.append( log.level ).append( ',' );

						if ( log.time != null )
						{
							sb.append( SysInfoManager.escapeCsv( log.time ) );
						}

						sb.append( ',' )
								.append( SysInfoManager.escapeCsv( log.msgList.get( k ) ) )
								.append( '\n' );
					}
				}
			}
		}
		else
		{
			sb.append( "TIME,LEVEL,TAG,PID,MSG\n" ); //$NON-NLS-1$

			for ( int i = 0; i < cnt; i++ )
			{
				LogItem log = (LogItem) adapter.getItem( i );

				if ( log.msgList == null )
				{
					sb.append( SysInfoManager.escapeCsv( log.time ) )
							.append( ',' )
							.append( log.level )
							.append( ',' )
							.append( SysInfoManager.escapeCsv( log.tag ) )
							.append( ',' )
							.append( log.pid )
							.append( ',' )
							.append( SysInfoManager.escapeCsv( log.msg ) )
							.append( '\n' );
				}
				else
				{
					for ( int k = 0, size = log.msgList.size( ); k < size; k++ )
					{
						sb.append( SysInfoManager.escapeCsv( log.time ) )
								.append( ',' )
								.append( log.level )
								.append( ',' )
								.append( SysInfoManager.escapeCsv( log.tag ) )
								.append( ',' )
								.append( log.pid )
								.append( ',' )
								.append( SysInfoManager.escapeCsv( log.msgList.get( k ) ) )
								.append( '\n' );
					}
				}
			}
		}

		return sb.toString( );
	}

	String collectHtmlLogContent( )
	{
		StringBuffer sb = new StringBuffer( );

		SysInfoManager.createHtmlHeader( this,
				sb,
				SysInfoManager.escapeHtml( "Android Device Log - " + new Date( ).toLocaleString( ) ) ); //$NON-NLS-1$

		ListAdapter adapter = getListView( ).getAdapter( );
		int cnt = adapter.getCount( );

		if ( dmesgMode )
		{
			sb.append( SysInfoManager.openHeaderRow )
					.append( "Dmesg " //$NON-NLS-1$
							+ getString( R.string.log ) )
					.append( SysInfoManager.closeHeaderRow );

			sb.append( SysInfoManager.openTitleRow ).append( "<b>" ) //$NON-NLS-1$
					.append( getString( R.string.log_level ) )
					.append( "</b>" ) //$NON-NLS-1$
					.append( SysInfoManager.nextColumn )
					.append( "<b>" ) //$NON-NLS-1$
					.append( getString( R.string.time ) )
					.append( "</b>" ) //$NON-NLS-1$
					.append( SysInfoManager.nextColumn )
					.append( "<b>" ) //$NON-NLS-1$
					.append( getString( R.string.message ) )
					.append( "</b>" ) //$NON-NLS-1$
					.append( SysInfoManager.closeRow );

			for ( int i = 0; i < cnt; i++ )
			{
				LogItem log = (LogItem) adapter.getItem( i );

				sb.append( SysInfoManager.openRow )
						.append( "<" + log.level + ">" ) //$NON-NLS-1$ //$NON-NLS-2$
						.append( SysInfoManager.nextColumn );

				sb.append( log.time == null ? "&nbsp;" //$NON-NLS-1$
						: SysInfoManager.escapeHtml( log.time ) )
						.append( SysInfoManager.nextColumn );

				sb.append( SysInfoManager.escapeHtml( log.getMsg( ) ) )
						.append( SysInfoManager.closeRow );
			}
		}
		else
		{
			sb.append( SysInfoManager.openHeaderRow )
					.append( "Logcat " //$NON-NLS-1$
							+ getString( R.string.log ) )
					.append( SysInfoManager.closeHeaderRow );

			sb.append( SysInfoManager.openTitleRow ).append( "<b>" ) //$NON-NLS-1$
					.append( getString( R.string.time ) )
					.append( "</b>" ) //$NON-NLS-1$
					.append( SysInfoManager.nextColumn )
					.append( "<b>" ) //$NON-NLS-1$
					.append( getString( R.string.log_level ) )
					.append( "</b>" ) //$NON-NLS-1$
					.append( SysInfoManager.nextColumn )
					.append( "<b>" ) //$NON-NLS-1$
					.append( getString( R.string.tag ) )
					.append( "</b>" ) //$NON-NLS-1$
					.append( SysInfoManager.nextColumn )
					.append( "<b>" ) //$NON-NLS-1$
					.append( getString( R.string.pid ) )
					.append( "</b>" ) //$NON-NLS-1$
					.append( SysInfoManager.nextColumn )
					.append( "<b>" ) //$NON-NLS-1$
					.append( getString( R.string.message ) )
					.append( "</b>" ) //$NON-NLS-1$
					.append( SysInfoManager.closeRow );

			for ( int i = 0; i < cnt; i++ )
			{
				LogItem log = (LogItem) adapter.getItem( i );

				sb.append( SysInfoManager.openRow )
						.append( SysInfoManager.escapeHtml( log.time ) )
						.append( SysInfoManager.nextColumn );

				sb.append( log.level ).append( SysInfoManager.nextColumn );
				sb.append( SysInfoManager.escapeHtml( log.tag ) )
						.append( SysInfoManager.nextColumn );
				sb.append( log.pid ).append( SysInfoManager.nextColumn );

				sb.append( SysInfoManager.escapeHtml( log.getMsg( ) ) )
						.append( SysInfoManager.closeRow );
			}
		}

		sb.append( SysInfoManager.emptyRow );

		sb.append( "</table></font></body></html>" ); //$NON-NLS-1$

		return sb.toString( );
	}

	private void refreshLogs( )
	{
		if ( progress != null )
		{
			progress.dismiss( );
		}
		progress = new ProgressDialog( this );
		progress.setMessage( getResources( ).getText( R.string.loading ) );
		progress.setIndeterminate( true );
		progress.show( );

		new Thread( new Runnable( ) {

			public void run( )
			{
				ArrayList<LogItem> logs = dmesgMode ? collectDLog( Util.getIntOption( LogViewer.this,
						PREF_KEY_DLOG_LEVL,
						DM_LVL_DEBUG ) )
						: collectCLog( Util.getIntOption( LogViewer.this,
								PREF_KEY_CLOG_LEVL,
								Log.VERBOSE ),
								Util.getIntOption( LogViewer.this,
										PREF_KEY_RING_BUFFER,
										RING_BUFFER_MAIN ),
								Util.getStringOption( LogViewer.this,
										PREF_KEY_TAG_FILTER,
										null ),
								Util.getIntOption( LogViewer.this,
										PREF_KEY_PID_FILTER,
										0 ) );

				handler.sendMessage( handler.obtainMessage( MSG_INIT_OK, logs ) );
			}
		} ).start( );
	}

	private static LogItem parseDLog( String line, char targetLevel )
	{
		char level = '6'; // default level as info
		int levelOffset = -1;

		if ( line.length( ) > 2
				&& line.charAt( 0 ) == '<'
				&& line.charAt( 2 ) == '>' )
		{
			level = line.charAt( 1 );
			levelOffset = 2;
		}

		if ( level > targetLevel )
		{
			return null;
		}

		LogItem log = new LogItem( );
		log.level = level;

		int timeOffset = line.indexOf( ']' );

		if ( timeOffset != -1 && timeOffset > levelOffset )
		{
			String timeRaw = line.substring( levelOffset + 1, timeOffset )
					.trim( );

			if ( timeRaw.length( ) > 1 && timeRaw.charAt( 0 ) == '[' )
			{
				timeRaw = timeRaw.substring( 1 );

				if ( DMESG_TIME_PATTERN.matcher( timeRaw ).find( ) )
				{
					log.time = timeRaw;
				}
				else
				{
					Log.d( LogViewer.class.getName( ),
							"Unexpected dmesg time value: " + line ); //$NON-NLS-1$
				}
			}
			else
			{
				Log.d( LogViewer.class.getName( ),
						"Unexpected dmesg time format: " + line ); //$NON-NLS-1$
			}
		}

		log.msg = line.substring( log.time == null ? ( levelOffset + 1 )
				: ( timeOffset + 1 ) ).trim( );

		return log;
	}

	private static LogItem parseCLog( String line, String tagFilter,
			int pidFilter )
	{
		if ( line.startsWith( "--------" ) ) //$NON-NLS-1$
		{
			// skip the header
			return null;
		}

		int dayOffset = line.indexOf( ' ' );
		int timeOffset = line.indexOf( ' ', dayOffset + 1 );
		int levelOffset = line.indexOf( '/', timeOffset + 1 );
		int pidOffset = line.indexOf( "):" ); //$NON-NLS-1$
		int tagOffset = line.lastIndexOf( '(', pidOffset );

		if ( dayOffset == -1
				|| timeOffset == -1
				|| levelOffset == -1
				|| pidOffset == -1
				|| tagOffset == -1 )
		{
			Log.d( LogViewer.class.getName( ),
					"Unexpected logcat line format: " + line ); //$NON-NLS-1$

			return null;
		}

		int pid = -1;
		try
		{
			String pidStr = line.substring( tagOffset + 1, pidOffset ).trim( );

			// some models have a special format like (num*num), need
			// investigation
			int idx = pidStr.indexOf( '*' );

			if ( idx != -1 )
			{
				pidStr = pidStr.substring( idx + 1 ).trim( );
			}

			pid = Integer.parseInt( pidStr );
		}
		catch ( Exception e )
		{
			Log.e( LogViewer.class.getName( ), e.getLocalizedMessage( ), e );
		}

		if ( pidFilter != 0 && pidFilter != pid )
		{
			return null;
		}

		String tag = line.substring( levelOffset + 1, tagOffset );

		if ( tagFilter != null && !tag.toLowerCase( ).contains( tagFilter ) )
		{
			return null;
		}

		LogItem log = new LogItem( );
		log.tag = tag;
		log.pid = pid;
		log.time = line.substring( 0, timeOffset );
		log.level = line.charAt( levelOffset - 1 );
		log.msg = line.substring( pidOffset + 2 ).trim( );

		return log;
	}

	static String formatDLog( LogItem log )
	{
		return "<" + log.level + "> " //$NON-NLS-1$ //$NON-NLS-2$
				+ ( log.time == null ? "" : ( "[" + log.time + "] " ) ); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
	}

	static String formatCLog( LogItem log )
	{
		return log.time + ' ' + log.level + '/' + log.tag + '(' + log.pid + ')';
	}

	static ArrayList<LogItem> collectDLog( int logLevel )
	{
		char dl = (char) ( logLevel + 0x30 );

		BufferedReader reader = null;
		try
		{
			Process proc = Runtime.getRuntime( ).exec( "dmesg" ); //$NON-NLS-1$

			reader = new BufferedReader( new InputStreamReader( proc.getInputStream( ) ),
					8192 );

			String line;
			LogItem dlog, lastDlog = null;

			ArrayList<LogItem> logs = new ArrayList<LogItem>( );

			while ( ( line = reader.readLine( ) ) != null )
			{
				dlog = parseDLog( line, dl );
				if ( dlog != null )
				{
					if ( lastDlog != null && lastDlog.mergable( dlog ) )
					{
						lastDlog.merge( dlog );
						continue;
					}

					lastDlog = dlog;
					logs.add( dlog );
				}
			}

			return logs;
		}
		catch ( Exception e )
		{
			Log.e( LogViewer.class.getName( ), e.getLocalizedMessage( ), e );
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
					Log.e( LogViewer.class.getName( ),
							e.getLocalizedMessage( ),
							e );
				}
			}
		}

		return null;
	}

	static ArrayList<LogItem> collectCLog( int logLevel, int ringBuffer,
			String tagFilter, int pidFilter )
	{
		char cl = 'V';

		switch ( logLevel )
		{
			case Log.DEBUG :
				cl = 'D';
				break;
			case Log.INFO :
				cl = 'I';
				break;
			case Log.WARN :
				cl = 'W';
				break;
			case Log.ERROR :
				cl = 'E';
				break;
			case Log.ASSERT :
				cl = 'F';
				break;
		}

		BufferedReader reader = null;
		try
		{
			String cmd = "logcat -d -v time *:"; //$NON-NLS-1$

			if ( ringBuffer == RING_BUFFER_RADIO )
			{
				cmd = "logcat -d -b radio -v time *:"; //$NON-NLS-1$
			}
			else if ( ringBuffer == RING_BUFFER_EVENTS )
			{
				cmd = "logcat -d -b events -v time *:"; //$NON-NLS-1$
			}

			Process proc = Runtime.getRuntime( ).exec( cmd + cl );

			reader = new BufferedReader( new InputStreamReader( proc.getInputStream( ) ),
					8192 );

			String line;
			LogItem clog, lastClog = null;

			ArrayList<LogItem> logs = new ArrayList<LogItem>( );

			if ( tagFilter != null )
			{
				tagFilter = tagFilter.toLowerCase( );
			}

			while ( ( line = reader.readLine( ) ) != null )
			{
				clog = parseCLog( line, tagFilter, pidFilter );
				if ( clog != null )
				{
					if ( lastClog != null && lastClog.mergable( clog ) )
					{
						lastClog.merge( clog );
						continue;
					}

					lastClog = clog;
					logs.add( clog );
				}
			}

			return logs;
		}
		catch ( Exception e )
		{
			Log.e( LogViewer.class.getName( ), e.getLocalizedMessage( ), e );
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
					Log.e( LogViewer.class.getName( ),
							e.getLocalizedMessage( ),
							e );
				}
			}
		}

		return null;
	}

	/**
	 * LogSettings
	 */
	public static final class LogSettings extends PreferenceActivity
	{

		boolean dmesgMode;

		@Override
		protected void onCreate( Bundle savedInstanceState )
		{
			requestWindowFeature( Window.FEATURE_NO_TITLE );

			super.onCreate( savedInstanceState );

			setPreferenceScreen( getPreferenceManager( ).createPreferenceScreen( this ) );

			dmesgMode = getIntent( ).getBooleanExtra( DMESG_MODE, false );

			PreferenceCategory pc = new PreferenceCategory( this );
			pc.setTitle( R.string.preference );
			getPreferenceScreen( ).addPreference( pc );

			Preference perfLevel = new Preference( this );
			perfLevel.setKey( "level_filter" ); //$NON-NLS-1$
			perfLevel.setTitle( R.string.log_level );
			pc.addPreference( perfLevel );

			if ( !dmesgMode )
			{
				Preference perfRingBuffer = new Preference( this );
				perfRingBuffer.setKey( PREF_KEY_RING_BUFFER );
				perfRingBuffer.setTitle( R.string.ring_buffer );
				pc.addPreference( perfRingBuffer );

				Preference perfTagFilter = new Preference( this );
				perfTagFilter.setKey( PREF_KEY_TAG_FILTER );
				perfTagFilter.setTitle( R.string.tag_filter );
				pc.addPreference( perfTagFilter );

				Preference perfPidFilter = new Preference( this );
				perfPidFilter.setKey( PREF_KEY_PID_FILTER );
				perfPidFilter.setTitle( R.string.pid_filter );
				pc.addPreference( perfPidFilter );

				refreshRingBuffer( );
				refreshTagFilter( );
				refreshPidFilter( );
			}

			refreshLevelFilter( );

			setResult( RESULT_OK, getIntent( ) );
		}

		void refreshLevelFilter( )
		{
			if ( dmesgMode )
			{
				int level = getIntent( ).getIntExtra( PREF_KEY_DLOG_LEVL,
						DM_LVL_DEBUG );

				CharSequence label = getString( R.string.debug );
				switch ( level )
				{
					case DM_LVL_EMMERGENCY :
						label = getString( R.string.emmergency );
						break;
					case DM_LVL_ALERT :
						label = getString( R.string.alert );
						break;
					case DM_LVL_CRITICAL :
						label = getString( R.string.critical );
						break;
					case DM_LVL_ERROR :
						label = getString( R.string.error );
						break;
					case DM_LVL_WARNING :
						label = getString( R.string.warning );
						break;
					case DM_LVL_NOTICE :
						label = getString( R.string.notice );
						break;
					case DM_LVL_INFORMATION :
						label = getString( R.string.info );
						break;
				}

				findPreference( "level_filter" ).setSummary( label ); //$NON-NLS-1$
			}
			else
			{
				int level = getIntent( ).getIntExtra( PREF_KEY_CLOG_LEVL,
						Log.VERBOSE );

				CharSequence label = getString( R.string.verbose );
				switch ( level )
				{
					case Log.DEBUG :
						label = getString( R.string.debug );
						break;
					case Log.INFO :
						label = getString( R.string.info );
						break;
					case Log.WARN :
						label = getString( R.string.warning );
						break;
					case Log.ERROR :
						label = getString( R.string.error );
						break;
					case Log.ASSERT :
						label = getString( R.string.asser_t );
						break;
				}

				findPreference( "level_filter" ).setSummary( label ); //$NON-NLS-1$
			}
		}

		void refreshRingBuffer( )
		{
			int buffer = getIntent( ).getIntExtra( PREF_KEY_RING_BUFFER,
					RING_BUFFER_MAIN );

			int label = R.string.main;

			switch ( buffer )
			{
				case RING_BUFFER_RADIO :
					label = R.string.radio;
					break;
				case RING_BUFFER_EVENTS :
					label = R.string.events;
					break;
			}

			findPreference( PREF_KEY_RING_BUFFER ).setSummary( label );
		}

		void refreshTagFilter( )
		{
			String tag = getIntent( ).getStringExtra( PREF_KEY_TAG_FILTER );

			if ( tag == null )
			{
				findPreference( PREF_KEY_TAG_FILTER ).setSummary( R.string.none );
			}
			else
			{
				findPreference( PREF_KEY_TAG_FILTER ).setSummary( tag );
			}
		}

		void refreshPidFilter( )
		{
			int pid = getIntent( ).getIntExtra( PREF_KEY_PID_FILTER, 0 );

			if ( pid == 0 )
			{
				findPreference( PREF_KEY_PID_FILTER ).setSummary( R.string.none );
			}
			else
			{
				findPreference( PREF_KEY_PID_FILTER ).setSummary( String.valueOf( pid ) );
			}
		}

		@Override
		public boolean onPreferenceTreeClick(
				PreferenceScreen preferenceScreen, Preference preference )
		{
			final Intent it = getIntent( );

			String prefKey = preference.getKey( );

			if ( "level_filter".equals( prefKey ) ) //$NON-NLS-1$
			{
				OnClickListener listener = new OnClickListener( ) {

					public void onClick( DialogInterface dialog, int which )
					{
						if ( dmesgMode )
						{
							it.putExtra( PREF_KEY_DLOG_LEVL, which );
						}
						else
						{
							it.putExtra( PREF_KEY_CLOG_LEVL, which + 2 );
						}

						dialog.dismiss( );

						refreshLevelFilter( );
					}
				};

				if ( dmesgMode )
				{
					new AlertDialog.Builder( this ).setTitle( R.string.log_level )
							.setNeutralButton( R.string.close, null )
							.setSingleChoiceItems( new CharSequence[]{

									Html.fromHtml( "<font color=\"#ff00ff\">● </font>" //$NON-NLS-1$
											+ getString( R.string.emmergency ) ),
									Html.fromHtml( "<font color=\"#F183BD\">● </font>" //$NON-NLS-1$
											+ getString( R.string.alert ) ),
									Html.fromHtml( "<font color=\"#8737CE\">● </font>" //$NON-NLS-1$
											+ getString( R.string.critical ) ),
									Html.fromHtml( "<font color=\"#ff0000\">● </font>" //$NON-NLS-1$
											+ getString( R.string.error ) ),
									Html.fromHtml( "<font color=\"#ffff00\">● </font>" //$NON-NLS-1$
											+ getString( R.string.warning ) ),
									Html.fromHtml( "<font color=\"#00ffff\">● </font>" //$NON-NLS-1$
											+ getString( R.string.notice ) ),
									Html.fromHtml( "<font color=\"#00ff00\">● </font>" //$NON-NLS-1$
											+ getString( R.string.info ) ),
									Html.fromHtml( "<font color=\"#888888\">● </font>" //$NON-NLS-1$
											+ getString( R.string.debug ) ),

							},
									it.getIntExtra( PREF_KEY_DLOG_LEVL,
											DM_LVL_DEBUG ),
									listener )
							.create( )
							.show( );
				}
				else
				{
					new AlertDialog.Builder( this ).setTitle( R.string.log_level )
							.setNeutralButton( R.string.close, null )
							.setSingleChoiceItems( new CharSequence[]{

									Html.fromHtml( "<font color=\"#888888\">● </font>" //$NON-NLS-1$
											+ getString( R.string.verbose ) ),
									Html.fromHtml( "<font color=\"#00ffff\">● </font>" //$NON-NLS-1$
											+ getString( R.string.debug ) ),
									Html.fromHtml( "<font color=\"#00ff00\">● </font>" //$NON-NLS-1$
											+ getString( R.string.info ) ),
									Html.fromHtml( "<font color=\"#ffff00\">● </font>" //$NON-NLS-1$
											+ getString( R.string.warning ) ),
									Html.fromHtml( "<font color=\"#ff0000\">● </font>" //$NON-NLS-1$
											+ getString( R.string.error ) ),
									Html.fromHtml( "<font color=\"#ff00ff\">● </font>" //$NON-NLS-1$
											+ getString( R.string.asser_t ) ),

							},
									it.getIntExtra( PREF_KEY_CLOG_LEVL,
											Log.VERBOSE ) - 2,
									listener )
							.create( )
							.show( );
				}

				return true;
			}
			else if ( PREF_KEY_RING_BUFFER.equals( prefKey ) )
			{
				OnClickListener listener = new OnClickListener( ) {

					public void onClick( DialogInterface dialog, int which )
					{
						it.putExtra( PREF_KEY_RING_BUFFER, which );

						dialog.dismiss( );

						refreshRingBuffer( );
					}
				};

				new AlertDialog.Builder( this ).setTitle( R.string.ring_buffer )
						.setNeutralButton( R.string.close, null )
						.setSingleChoiceItems( new CharSequence[]{
								getString( R.string.main ),
								getString( R.string.radio ),
								getString( R.string.events ),
						},
								it.getIntExtra( PREF_KEY_RING_BUFFER,
										RING_BUFFER_MAIN ),
								listener )
						.create( )
						.show( );

				return true;
			}
			else if ( PREF_KEY_TAG_FILTER.equals( prefKey ) )
			{
				final EditText txt = new EditText( this );
				txt.setText( it.getStringExtra( PREF_KEY_TAG_FILTER ) );

				OnClickListener listener = new OnClickListener( ) {

					public void onClick( DialogInterface dialog, int which )
					{
						String filter = txt.getText( ).toString( );

						if ( filter != null )
						{
							filter = filter.trim( );

							if ( filter.length( ) == 0 )
							{
								filter = null;
							}
						}

						it.putExtra( PREF_KEY_TAG_FILTER, filter );

						dialog.dismiss( );

						refreshTagFilter( );
					}
				};

				new AlertDialog.Builder( this ).setTitle( R.string.tag_filter )
						.setPositiveButton( android.R.string.ok, listener )
						.setNegativeButton( android.R.string.cancel, null )
						.setView( txt )
						.create( )
						.show( );

				return true;
			}
			else if ( PREF_KEY_PID_FILTER.equals( prefKey ) )
			{
				final EditText txt = new EditText( this );
				txt.setFilters( new InputFilter[]{
					DigitsKeyListener.getInstance( false, false )
				} );

				int pid = it.getIntExtra( PREF_KEY_PID_FILTER, 0 );
				if ( pid != 0 )
				{
					txt.setText( String.valueOf( pid ) );
				}

				OnClickListener listener = new OnClickListener( ) {

					public void onClick( DialogInterface dialog, int which )
					{
						String filter = txt.getText( ).toString( );

						if ( filter != null )
						{
							filter = filter.trim( );

							if ( filter.length( ) == 0 )
							{
								filter = null;
							}
						}

						int pid = 0;

						if ( filter != null )
						{
							try
							{
								pid = Integer.parseInt( filter );
							}
							catch ( Exception e )
							{
								Log.e( LogSettings.class.getName( ),
										e.getLocalizedMessage( ),
										e );
							}
						}

						it.putExtra( PREF_KEY_PID_FILTER, pid );

						dialog.dismiss( );

						refreshPidFilter( );
					}
				};

				new AlertDialog.Builder( this ).setTitle( R.string.pid_filter )
						.setPositiveButton( android.R.string.ok, listener )
						.setNegativeButton( android.R.string.cancel, null )
						.setView( txt )
						.create( )
						.show( );

				return true;
			}
			return false;
		}
	}

	/**
	 * LogItem
	 */
	private static final class LogItem
	{

		char level;
		String tag;
		int pid;
		String time;
		String msg;
		ArrayList<String> msgList;

		LogItem( )
		{

		}

		String getMsg( )
		{
			if ( msg == null && msgList != null )
			{
				StringBuffer sb = new StringBuffer( );

				for ( int k = 0, size = msgList.size( ); k < size; k++ )
				{
					sb.append( msgList.get( k ) ).append( '\n' );
				}

				msg = sb.toString( ).trim( );
			}

			return msg;
		}

		void merge( LogItem that )
		{
			if ( msgList == null )
			{
				msgList = new ArrayList<String>( );
				msgList.add( msg );
				msg = null;
			}

			msgList.add( that.msg );
		}

		boolean mergable( LogItem that )
		{
			if ( this.level != that.level || this.pid != that.pid )
			{
				return false;
			}

			if ( !TextUtils.equals( this.tag, that.tag ) )
			{
				return false;
			}

			return TextUtils.equals( this.time, that.time );
		}
	}

}