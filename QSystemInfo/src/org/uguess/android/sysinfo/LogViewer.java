
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
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.DialogInterface.OnClickListener;
import android.content.SharedPreferences.Editor;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceGroup;
import android.preference.PreferenceScreen;
import android.text.ClipboardManager;
import android.text.InputFilter;
import android.text.TextUtils;
import android.text.method.DigitsKeyListener;
import android.util.Log;
import android.view.ContextMenu;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.ContextMenu.ContextMenuInfo;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ListAdapter;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.AdapterView.AdapterContextMenuInfo;

/**
 * LogViewer
 */
public final class LogViewer extends ListActivity
{

	private static final Pattern DMESG_TIME_PATTERN = Pattern.compile( "\\d+\\.\\d+" ); //$NON-NLS-1$

	private static final String PREF_KEY_CLOG_LEVL = "clog_level"; //$NON-NLS-1$
	private static final String PREF_KEY_DLOG_LEVL = "dlog_level"; //$NON-NLS-1$
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

	static final String DMESG_MODE = "dmesgMode"; //$NON-NLS-1$

	private boolean dmesgMode;
	private ProgressDialog progress;

	private Handler handler = new Handler( ) {

		public void handleMessage( android.os.Message msg )
		{
			switch ( msg.what )
			{
				case SysInfoManager.MSG_INIT_OK :

					ArrayAdapter<LogItem> adapter = (ArrayAdapter<LogItem>) getListView( ).getAdapter( );

					adapter.setNotifyOnChange( false );

					adapter.clear( );

					ArrayList<LogItem> data = (ArrayList<LogItem>) msg.obj;

					if ( data != null )
					{
						for ( LogItem log : data )
						{
							adapter.add( log );
						}
					}

					adapter.notifyDataSetChanged( );

					sendEmptyMessage( SysInfoManager.MSG_DISMISS_PROGRESS );

					if ( adapter.getCount( ) == 0 )
					{
						Toast.makeText( LogViewer.this,
								R.string.no_log_info,
								Toast.LENGTH_SHORT ).show( );
					}
					else
					{
						getListView( ).setSelection( adapter.getCount( ) - 1 );
					}

					break;
				case SysInfoManager.MSG_CONTENT_READY :

					sendEmptyMessage( SysInfoManager.MSG_DISMISS_PROGRESS );

					String content = (String) msg.obj;

					if ( content == null )
					{
						Toast.makeText( LogViewer.this,
								R.string.no_log_info,
								Toast.LENGTH_SHORT ).show( );
					}
					else
					{
						SysInfoManager.sendContent( LogViewer.this,
								"Android Device Log - " + new Date( ).toLocaleString( ), //$NON-NLS-1$
								content,
								msg.arg2 == 1 );
					}

					break;
				case SysInfoManager.MSG_CHECK_FORCE_COMPRESSION :

					sendEmptyMessage( SysInfoManager.MSG_DISMISS_PROGRESS );

					SysInfoManager.checkForceCompression( this,
							LogViewer.this,
							(String) msg.obj,
							msg.arg1,
							"android_log" ); //$NON-NLS-1$

					break;
				case SysInfoManager.MSG_DISMISS_PROGRESS :

					if ( progress != null )
					{
						progress.dismiss( );
						progress = null;
					}
					break;
				case SysInfoManager.MSG_TOAST :

					Toast.makeText( LogViewer.this,
							(String) msg.obj,
							Toast.LENGTH_SHORT ).show( );
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
							txt_head.setTextColor( Color.MAGENTA );
							break;
						case '2' :
							txt_head.setTextColor( Color.RED );
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
			}
		}

		return true;
	}

	@Override
	public boolean onCreateOptionsMenu( Menu menu )
	{
		getMenuInflater( ).inflate( R.menu.log_options, menu );

		return true;
	}

	@Override
	public boolean onOptionsItemSelected( MenuItem item )
	{
		if ( item.getItemId( ) == R.id.mi_preference )
		{
			Intent it = new Intent( Intent.ACTION_VIEW );
			it.setClass( this, LogSettings.class );

			it.putExtra( DMESG_MODE, dmesgMode );

			it.putExtra( PREF_KEY_CLOG_LEVL, getCLogLevel( ) );
			it.putExtra( PREF_KEY_TAG_FILTER, getCTagFilter( ) );
			it.putExtra( PREF_KEY_PID_FILTER, getPIDFilter( ) );

			it.putExtra( PREF_KEY_DLOG_LEVL, getDLogLevel( ) );

			startActivityForResult( it, 1 );

			return true;
		}
		else if ( item.getItemId( ) == R.id.mi_send_log )
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

	private void sendLog( final boolean compressed, final int format )
	{
		if ( progress == null )
		{
			progress = new ProgressDialog( this );
		}
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
					content = SysInfoManager.createCompressedContent( handler,
							LogViewer.this,
							content,
							format,
							"android_log" ); //$NON-NLS-1$
				}

				if ( content != null && !compressed )
				{
					handler.sendMessage( handler.obtainMessage( SysInfoManager.MSG_CHECK_FORCE_COMPRESSION,
							format,
							compressed ? 1 : 0,
							content ) );
				}
				else
				{
					handler.sendMessage( handler.obtainMessage( SysInfoManager.MSG_CONTENT_READY,
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
		if ( requestCode == 1 )
		{
			boolean needRefresh = false;

			int logLevel = data.getIntExtra( PREF_KEY_CLOG_LEVL, Log.VERBOSE );
			if ( logLevel != getCLogLevel( ) )
			{
				setCLogLevel( logLevel );
				needRefresh = true;
			}

			String tagFilter = data.getStringExtra( PREF_KEY_TAG_FILTER );
			if ( !TextUtils.equals( tagFilter, getCTagFilter( ) ) )
			{
				setCTagFilter( tagFilter );
				needRefresh = true;
			}

			int pidFilter = data.getIntExtra( PREF_KEY_PID_FILTER, 0 );
			if ( pidFilter != getPIDFilter( ) )
			{
				setPIDFilter( pidFilter );
				needRefresh = true;
			}

			logLevel = data.getIntExtra( PREF_KEY_DLOG_LEVL, DM_LVL_DEBUG );
			if ( logLevel != getDLogLevel( ) )
			{
				setDLogLevel( logLevel );
				needRefresh = true;
			}

			if ( needRefresh )
			{
				refreshLogs( );
			}
		}
	}

	private String collectTextLogContent( )
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
					for ( String s : log.msgList )
					{
						sb.append( head ).append( s ).append( '\n' );
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
					for ( String s : log.msgList )
					{
						sb.append( head ).append( s ).append( '\n' );
					}
				}
			}
		}

		return sb.toString( );
	}

	private String collectCSVLogContent( )
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
					for ( String s : log.msgList )
					{
						sb.append( log.level ).append( ',' );

						if ( log.time != null )
						{
							sb.append( SysInfoManager.escapeCsv( log.time ) );
						}

						sb.append( ',' )
								.append( SysInfoManager.escapeCsv( s ) )
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
					for ( String s : log.msgList )
					{
						sb.append( SysInfoManager.escapeCsv( log.time ) )
								.append( ',' )
								.append( log.level )
								.append( ',' )
								.append( SysInfoManager.escapeCsv( log.tag ) )
								.append( ',' )
								.append( log.pid )
								.append( ',' )
								.append( SysInfoManager.escapeCsv( s ) )
								.append( '\n' );
					}
				}
			}
		}

		return sb.toString( );
	}

	private String collectHtmlLogContent( )
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

	private int getCLogLevel( )
	{
		SharedPreferences sp = getPreferences( Context.MODE_PRIVATE );

		return sp.getInt( PREF_KEY_CLOG_LEVL, Log.VERBOSE );
	}

	private void setCLogLevel( int level )
	{
		SharedPreferences sp = getPreferences( Context.MODE_PRIVATE );

		Editor et = sp.edit( );
		et.putInt( PREF_KEY_CLOG_LEVL, level );
		et.commit( );
	}

	private String getCTagFilter( )
	{
		SharedPreferences sp = getPreferences( Context.MODE_PRIVATE );

		return sp.getString( PREF_KEY_TAG_FILTER, null );
	}

	private void setCTagFilter( String val )
	{
		SharedPreferences sp = getPreferences( Context.MODE_PRIVATE );

		Editor et = sp.edit( );
		if ( val == null )
		{
			et.remove( PREF_KEY_TAG_FILTER );
		}
		else
		{
			et.putString( PREF_KEY_TAG_FILTER, val );
		}
		et.commit( );
	}

	private int getPIDFilter( )
	{
		SharedPreferences sp = getPreferences( Context.MODE_PRIVATE );

		return sp.getInt( PREF_KEY_PID_FILTER, 0 );
	}

	private void setPIDFilter( int pid )
	{
		SharedPreferences sp = getPreferences( Context.MODE_PRIVATE );

		Editor et = sp.edit( );
		et.putInt( PREF_KEY_PID_FILTER, pid );
		et.commit( );
	}

	private int getDLogLevel( )
	{
		SharedPreferences sp = getPreferences( Context.MODE_PRIVATE );

		return sp.getInt( PREF_KEY_DLOG_LEVL, DM_LVL_DEBUG );
	}

	private void setDLogLevel( int level )
	{
		SharedPreferences sp = getPreferences( Context.MODE_PRIVATE );

		Editor et = sp.edit( );
		et.putInt( PREF_KEY_DLOG_LEVL, level );
		et.commit( );
	}

	private void refreshLogs( )
	{
		if ( progress == null )
		{
			progress = new ProgressDialog( this );
		}
		progress.setMessage( getResources( ).getText( R.string.loading ) );
		progress.setIndeterminate( true );
		progress.show( );

		new Thread( new Runnable( ) {

			public void run( )
			{
				ArrayList<LogItem> logs = dmesgMode ? collectDLog( getDLogLevel( ) )
						: collectCLog( getCLogLevel( ),
								getCTagFilter( ),
								getPIDFilter( ) );

				handler.sendMessage( handler.obtainMessage( SysInfoManager.MSG_INIT_OK,
						logs ) );
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
		int dayOffset = line.indexOf( ' ' );
		int timeOffset = line.indexOf( ' ', dayOffset + 1 );
		int levelOffset = line.indexOf( '/', timeOffset + 1 );
		int pidOffset = line.indexOf( "):" ); //$NON-NLS-1$
		int tagOffset = line.lastIndexOf( '(', pidOffset );

		int pid = -1;
		try
		{
			pid = Integer.parseInt( line.substring( tagOffset + 1, pidOffset )
					.trim( ) );
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

		if ( tagFilter != null && !tag.contains( tagFilter ) )
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

	private static String formatDLog( LogItem log )
	{
		return "<" + log.level + "> " //$NON-NLS-1$ //$NON-NLS-2$
				+ ( log.time == null ? "" : ( "[" + log.time + "] " ) ); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
	}

	private static String formatCLog( LogItem log )
	{
		return log.time + ' ' + log.level + '/' + log.tag + '(' + log.pid + ')';
	}

	private static ArrayList<LogItem> collectDLog( int logLevel )
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

	private static ArrayList<LogItem> collectCLog( int logLevel,
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
			Process proc = Runtime.getRuntime( )
					.exec( "logcat -d -v time *:" + cl ); //$NON-NLS-1$

			reader = new BufferedReader( new InputStreamReader( proc.getInputStream( ) ),
					8192 * 4 );

			String line;
			LogItem clog, lastClog = null;

			ArrayList<LogItem> logs = new ArrayList<LogItem>( );

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

		private boolean dmesgMode;

		@Override
		protected void onCreate( Bundle savedInstanceState )
		{
			requestWindowFeature( Window.FEATURE_NO_TITLE );

			super.onCreate( savedInstanceState );

			addPreferencesFromResource( R.xml.log_pref );

			dmesgMode = getIntent( ).getBooleanExtra( DMESG_MODE, false );

			if ( dmesgMode )
			{
				( (PreferenceGroup) getPreferenceScreen( ).getPreference( 0 ) ).removePreference( findPreference( "tag_filter" ) ); //$NON-NLS-1$
				( (PreferenceGroup) getPreferenceScreen( ).getPreference( 0 ) ).removePreference( findPreference( "pid_filter" ) ); //$NON-NLS-1$
			}
			else
			{
				refreshTagFilter( );
				refreshPidFilter( );
			}

			refreshLevelFilter( );

			setResult( RESULT_OK, getIntent( ) );
		}

		private void refreshLevelFilter( )
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

		private void refreshTagFilter( )
		{
			String tag = getIntent( ).getStringExtra( PREF_KEY_TAG_FILTER );

			if ( tag == null )
			{
				findPreference( "tag_filter" ).setSummary( R.string.none ); //$NON-NLS-1$
			}
			else
			{
				findPreference( "tag_filter" ).setSummary( tag ); //$NON-NLS-1$
			}
		}

		private void refreshPidFilter( )
		{
			int pid = getIntent( ).getIntExtra( PREF_KEY_PID_FILTER, 0 );

			if ( pid == 0 )
			{
				findPreference( "pid_filter" ).setSummary( R.string.none ); //$NON-NLS-1$
			}
			else
			{
				findPreference( "pid_filter" ).setSummary( String.valueOf( pid ) ); //$NON-NLS-1$
			}
		}

		@Override
		public boolean onPreferenceTreeClick(
				PreferenceScreen preferenceScreen, Preference preference )
		{
			final Intent it = getIntent( );

			if ( "level_filter".equals( preference.getKey( ) ) ) //$NON-NLS-1$
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
									getString( R.string.emmergency ),
									getString( R.string.alert ),
									getString( R.string.critical ),
									getString( R.string.error ),
									getString( R.string.warning ),
									getString( R.string.notice ),
									getString( R.string.info ),
									getString( R.string.debug )
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
									getString( R.string.verbose ),
									getString( R.string.debug ),
									getString( R.string.info ),
									getString( R.string.warning ),
									getString( R.string.error ),
									getString( R.string.asser_t )
							},
									it.getIntExtra( PREF_KEY_CLOG_LEVL,
											Log.VERBOSE ) - 2,
									listener )
							.create( )
							.show( );
				}

				return true;
			}
			else if ( "tag_filter".equals( preference.getKey( ) ) ) //$NON-NLS-1$
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
			else if ( "pid_filter".equals( preference.getKey( ) ) ) //$NON-NLS-1$
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

		String getMsg( )
		{
			if ( msg == null && msgList != null )
			{
				StringBuffer sb = new StringBuffer( );

				for ( String s : msgList )
				{
					sb.append( s ).append( '\n' );
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