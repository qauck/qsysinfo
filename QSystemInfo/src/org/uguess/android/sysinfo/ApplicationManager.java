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

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.text.Collator;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.CountDownLatch;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ListActivity;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.DialogInterface.OnMultiChoiceClickListener;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.IPackageStatsObserver;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.pm.PackageStats;
import android.content.pm.ResolveInfo;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.os.RemoteException;
import android.preference.CheckBoxPreference;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceScreen;
import android.text.Html;
import android.text.format.Formatter;
import android.util.Log;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.view.animation.AnimationUtils;
import android.widget.AdapterView;
import android.widget.AdapterView.AdapterContextMenuInfo;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;

/**
 * ApplicationManager
 */
public final class ApplicationManager extends ListActivity implements Constants
{

	private static final int MSG_COPING = MSG_PRIVATE + 1;
	private static final int MSG_COPING_ERROR = MSG_PRIVATE + 2;
	private static final int MSG_COPING_FINISHED = MSG_PRIVATE + 3;
	private static final int MSG_REFRESH_PKG_SIZE = MSG_PRIVATE + 4;
	private static final int MSG_REFRESH_PKG_LABEL = MSG_PRIVATE + 5;
	private static final int MSG_REFRESH_PKG_ICON = MSG_PRIVATE + 6;
	private static final int MSG_REFRESH_BACKUP_STATE = MSG_PRIVATE + 7;
	private static final int MSG_UPDATE = MSG_PRIVATE + 8;

	private static final int APP_TYPE_ALL = 0;
	private static final int APP_TYPE_SYS = 1;
	private static final int APP_TYPE_USER = 2;

	private static final int ORDER_TYPE_NAME = 0;
	private static final int ORDER_TYPE_CODE_SIZE = 1;
	private static final int ORDER_TYPE_DATA_SIZE = 2;
	private static final int ORDER_TYPE_CACHE_SIZE = 3;
	private static final int ORDER_TYPE_TOTAL_SIZE = 4;
	private static final int ORDER_TYPE_INSTALL_DATE = 5;
	private static final int ORDER_TYPE_BACKUP_STATE = 6;

	private static final int REQUEST_SETTINGS = 1;
	private static final int REQUEST_RESTORE = 2;

	private static final String PREF_KEY_FILTER_APP_TYPE = "filter_app_type"; //$NON-NLS-1$
	private static final String PREF_KEY_APP_EXPORT_DIR = "app_export_dir"; //$NON-NLS-1$
	private static final String PREF_KEY_SHOW_BACKUP_STATE = "show_backup_state"; //$NON-NLS-1$

	static final String KEY_RESTORE_PATH = "restore_path"; //$NON-NLS-1$

	private static final String DEFAULT_EXPORT_FOLDER = "/sdcard/backups/"; //$NON-NLS-1$

	private static final String SYS_APP = "system/"; //$NON-NLS-1$

	private static final String USER_APP = "user/"; //$NON-NLS-1$

	static Method mdGetPackageSizeInfo;

	static
	{
		try
		{
			mdGetPackageSizeInfo = PackageManager.class.getMethod( "getPackageSizeInfo", //$NON-NLS-1$
					String.class,
					IPackageStatsObserver.class );
		}
		catch ( Exception e )
		{
			Log.e( ApplicationManager.class.getName( ),
					e.getLocalizedMessage( ),
					e );
		}
	}

	ProgressDialog progress;

	volatile boolean aborted;

	String versionPrefix;

	AppCache appCache;

	DateFormat dateFormatter = DateFormat.getDateTimeInstance( );

	PkgSizeUpdaterThread sizeUpdater;

	ResourceUpdaterThread resUpdater;

	BackupStateUpdaterThread backupUpdater;

	Handler handler = new Handler( ) {

		@Override
		public void handleMessage( Message msg )
		{
			ArrayAdapter<AppInfoHolder> adapter;

			switch ( msg.what )
			{
				case MSG_INIT_OK :

					adapter = (ArrayAdapter<AppInfoHolder>) getListAdapter( );

					adapter.setNotifyOnChange( false );

					adapter.clear( );

					ArrayList<AppInfoHolder> localList = appCache.appList;

					for ( int i = 0, size = localList.size( ); i < size; i++ )
					{
						adapter.add( localList.get( i ) );
					}

					// should always no selection at this stage
					hideButtons( );

					adapter.notifyDataSetChanged( );

					sendEmptyMessage( MSG_DISMISS_PROGRESS );

					if ( getListView( ).getCount( ) == 0 )
					{
						Util.shortToast( ApplicationManager.this,
								R.string.no_app_show );
					}

					break;
				case MSG_COPING :

					if ( progress != null )
					{
						progress.setMessage( getString( R.string.exporting,
								msg.obj ) );
						progress.setProgress( progress.getProgress( ) + 1 );
					}
					break;
				case MSG_COPING_ERROR :

					if ( msg.arg1 == 0 && progress != null )
					{
						progress.dismiss( );
						progress = null;
					}

					Util.shortToast( ApplicationManager.this,
							getString( R.string.copy_error, msg.obj ) );
					break;
				case MSG_COPING_FINISHED :

					final List<AppInfoHolder> apps = (List<AppInfoHolder>) msg.obj;

					if ( progress != null )
					{
						progress.setMessage( msg.arg2 > 0 ? getString( R.string.exported_skip,
								msg.arg1,
								msg.arg2 )
								: getString( R.string.exported, msg.arg1 ) );
						progress.setProgress( progress.getMax( ) );
						progress.dismiss( );
						progress = null;
					}

					Util.shortToast( ApplicationManager.this,
							msg.arg2 > 0 ? getString( R.string.exported_to_skip,
									msg.arg1,
									Util.getStringOption( ApplicationManager.this,
											PREF_KEY_APP_EXPORT_DIR,
											DEFAULT_EXPORT_FOLDER ),
									msg.arg2 )
									: getString( R.string.exported_to,
											msg.arg1,
											Util.getStringOption( ApplicationManager.this,
													PREF_KEY_APP_EXPORT_DIR,
													DEFAULT_EXPORT_FOLDER ) ) );

					Notification nc = new Notification( R.drawable.icon,
							getResources( ).getString( R.string.export_complete ),
							System.currentTimeMillis( ) );

					PendingIntent pit = PendingIntent.getActivity( ApplicationManager.this,
							0,
							new Intent( ),
							0 );

					nc.flags |= Notification.FLAG_AUTO_CANCEL;
					nc.setLatestEventInfo( ApplicationManager.this,
							getResources( ).getString( R.string.export_complete ),
							msg.arg2 > 0 ? getString( R.string.exported_skip,
									msg.arg1,
									msg.arg2 ) : getString( R.string.exported,
									msg.arg1 ),
							pit );

					( (NotificationManager) getSystemService( NOTIFICATION_SERVICE ) ).notify( NOTIFY_EXPORT_FINISHED,
							nc );

					toggleAllSelection( false );

					if ( Util.getBooleanOption( ApplicationManager.this,
							PREF_KEY_SHOW_BACKUP_STATE ) )
					{
						// reload backup state

						if ( backupUpdater != null )
						{
							backupUpdater.aborted = true;
						}

						( backupUpdater = new BackupStateUpdaterThread( ApplicationManager.this,
								apps,
								appCache,
								handler ) ).start( );;
					}
					break;
				case MSG_DISMISS_PROGRESS :

					if ( progress != null )
					{
						progress.dismiss( );
						progress = null;
					}
					break;
				case MSG_REFRESH_PKG_SIZE :
				case MSG_REFRESH_PKG_LABEL :
				case MSG_REFRESH_BACKUP_STATE :

					adapter = (ArrayAdapter<AppInfoHolder>) getListAdapter( );

					if ( msg.arg1 == 1 )
					{
						adapter.setNotifyOnChange( false );

						adapter.clear( );

						localList = appCache.appList;

						for ( int i = 0, size = localList.size( ); i < size; i++ )
						{
							adapter.add( localList.get( i ) );
						}
					}

					adapter.notifyDataSetChanged( );
					break;
				case MSG_REFRESH_PKG_ICON :

					( (ArrayAdapter<AppInfoHolder>) getListAdapter( ) ).notifyDataSetChanged( );
					break;
				case MSG_TOAST :

					Util.shortToast( ApplicationManager.this, (String) msg.obj );
					break;
				case MSG_UPDATE :

					if ( sizeUpdater != null )
					{
						sizeUpdater.aborted = true;
					}

					if ( resUpdater != null )
					{
						resUpdater.aborted = true;
					}

					if ( backupUpdater != null )
					{
						backupUpdater.aborted = true;
					}

					appCache.update( (ArrayList<AppInfoHolder>) msg.obj );

					appCache.reOrder( Util.getIntOption( ApplicationManager.this,
							PREF_KEY_SORT_ORDER_TYPE,
							ORDER_TYPE_NAME ),
							Util.getIntOption( ApplicationManager.this,
									PREF_KEY_SORT_DIRECTION,
									ORDER_ASC ) );

					handler.sendEmptyMessage( MSG_INIT_OK );

					( sizeUpdater = new PkgSizeUpdaterThread( ApplicationManager.this,
							appCache,
							handler ) ).start( );

					( resUpdater = new ResourceUpdaterThread( ApplicationManager.this,
							appCache,
							handler ) ).start( );

					if ( Util.getBooleanOption( ApplicationManager.this,
							PREF_KEY_SHOW_BACKUP_STATE ) )
					{
						( backupUpdater = new BackupStateUpdaterThread( ApplicationManager.this,
								null,
								appCache,
								handler ) ).start( );
					}
					break;
				case MSG_CONTENT_READY :

					sendEmptyMessage( MSG_DISMISS_PROGRESS );

					Util.handleMsgSendContentReady( (String) msg.obj,
							"Android Applications - ", //$NON-NLS-1$
							ApplicationManager.this,
							msg.arg2 == 1 );

					break;
				case MSG_CHECK_FORCE_COMPRESSION :

					sendEmptyMessage( MSG_DISMISS_PROGRESS );

					Util.checkForceCompression( this,
							ApplicationManager.this,
							(String) msg.obj,
							msg.arg1,
							"android_applications" ); //$NON-NLS-1$

					break;
			}
		}
	};

	OnCheckedChangeListener checkListener = new OnCheckedChangeListener( ) {

		public void onCheckedChanged( CompoundButton buttonView,
				boolean isChecked )
		{
			( (AppInfoHolder) getListView( ).getItemAtPosition( (Integer) buttonView.getTag( ) ) ).checked = isChecked;

			View v = findViewById( R.id.app_footer );

			if ( isChecked )
			{
				if ( v.getVisibility( ) != View.VISIBLE )
				{
					v.setVisibility( View.VISIBLE );

					v.startAnimation( AnimationUtils.loadAnimation( ApplicationManager.this,
							R.anim.footer_appear ) );
				}
			}
			else if ( getSelectedCount( getListView( ) ) == 0 )
			{
				hideButtons( );
			}
		}
	};

	@Override
	protected void onCreate( Bundle savedInstanceState )
	{
		super.onCreate( savedInstanceState );

		versionPrefix = getResources( ).getString( R.string.version );

		appCache = new AppCache( );

		setContentView( R.layout.app_view );

		( (Button) findViewById( R.id.btn_export ) ).setOnClickListener( new View.OnClickListener( ) {

			public void onClick( View v )
			{
				doExport( );
			}
		} );

		( (Button) findViewById( R.id.btn_sel_all ) ).setOnClickListener( new View.OnClickListener( ) {

			public void onClick( View v )
			{
				toggleAllSelection( true );
			}
		} );

		( (Button) findViewById( R.id.btn_desel_all ) ).setOnClickListener( new View.OnClickListener( ) {

			public void onClick( View v )
			{
				toggleAllSelection( false );
			}
		} );

		ListView lstApps = getListView( );

		lstApps.setFastScrollEnabled( true );

		registerForContextMenu( lstApps );

		lstApps.setOnItemClickListener( new OnItemClickListener( ) {

			public void onItemClick( AdapterView<?> parent, View view,
					int position, long id )
			{
				AppInfoHolder holder = (AppInfoHolder) parent.getItemAtPosition( position );

				Intent it = new Intent( Intent.ACTION_VIEW );

				it.setClassName( "com.android.settings", //$NON-NLS-1$
						"com.android.settings.InstalledAppDetails" ); //$NON-NLS-1$
				it.putExtra( "com.android.settings.ApplicationPkgName", //$NON-NLS-1$
						holder.appInfo.packageName );

				List<ResolveInfo> acts = getPackageManager( ).queryIntentActivities( it,
						0 );

				if ( acts.size( ) > 0 )
				{
					startActivity( it );
				}
				else
				{
					Log.d( ApplicationManager.class.getName( ),
							"Failed to resolve activity for InstalledAppDetails" ); //$NON-NLS-1$
				}
			}
		} );

		ArrayAdapter<AppInfoHolder> adapter = new ArrayAdapter<AppInfoHolder>( ApplicationManager.this,
				R.layout.app_item ) {

			public android.view.View getView( int position,
					android.view.View convertView, android.view.ViewGroup parent )
			{
				View view;
				TextView txt_name, txt_size, txt_ver, txt_time;
				ImageView img_type;
				CheckBox ckb_app;

				if ( convertView == null )
				{
					view = ApplicationManager.this.getLayoutInflater( )
							.inflate( R.layout.app_item, parent, false );
				}
				else
				{
					view = convertView;
				}

				AppInfoHolder itm = getItem( position );

				txt_name = (TextView) view.findViewById( R.id.app_name );
				if ( itm.label != null )
				{
					txt_name.setText( itm.label );
				}
				else
				{
					txt_name.setText( itm.appInfo.packageName );
				}

				if ( Util.getBooleanOption( ApplicationManager.this,
						PREF_KEY_SHOW_BACKUP_STATE ) )
				{
					switch ( itm.backupState )
					{
						case 1 :
							txt_name.setTextColor( Color.YELLOW );
							break;
						case 2 :
							txt_name.setTextColor( 0xff00bb00 );
							break;
						case 3 :
							txt_name.setTextColor( 0xffF183BD );
							break;
						default :
							txt_name.setTextColor( Color.WHITE );
							break;
					}
				}
				else
				{
					txt_name.setTextColor( Color.WHITE );
				}

				txt_ver = (TextView) view.findViewById( R.id.app_version );
				txt_ver.setText( itm.version );

				txt_size = (TextView) view.findViewById( R.id.app_size );
				if ( Util.getBooleanOption( ApplicationManager.this,
						PREF_KEY_SHOW_SIZE ) )
				{
					txt_size.setVisibility( View.VISIBLE );

					if ( itm.size != null )
					{
						txt_size.setText( itm.size );
					}
					else
					{
						txt_size.setText( R.string.computing );
					}
				}
				else
				{
					txt_size.setVisibility( View.GONE );
				}

				txt_time = (TextView) view.findViewById( R.id.app_time );
				if ( Util.getBooleanOption( ApplicationManager.this,
						PREF_KEY_SHOW_DATE ) )
				{
					txt_time.setVisibility( View.VISIBLE );

					if ( itm.appInfo.sourceDir != null )
					{
						File f = new File( itm.appInfo.sourceDir );
						txt_time.setText( dateFormatter.format( new Date( f.lastModified( ) ) ) );
					}
					else
					{
						txt_time.setText( R.string.unknown );
					}
				}
				else
				{
					txt_time.setVisibility( View.GONE );
				}

				img_type = (ImageView) view.findViewById( R.id.img_app_icon );
				if ( itm.icon != null )
				{
					img_type.setImageDrawable( itm.icon );
				}
				else
				{
					img_type.setImageDrawable( getResources( ).getDrawable( R.drawable.icon ) );
				}

				ckb_app = (CheckBox) view.findViewById( R.id.ckb_app );
				ckb_app.setTag( position );
				ckb_app.setChecked( itm.checked );
				ckb_app.setOnCheckedChangeListener( checkListener );

				View imgLock = view.findViewById( R.id.img_lock );
				imgLock.setVisibility( itm.isPrivate ? View.VISIBLE : View.GONE );

				return view;
			}
		};

		setListAdapter( adapter );
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
	protected void onStart( )
	{
		super.onStart( );

		loadApps( );
	}

	@Override
	protected void onStop( )
	{
		if ( sizeUpdater != null )
		{
			sizeUpdater.aborted = true;
		}

		if ( resUpdater != null )
		{
			resUpdater.aborted = true;
		}

		if ( backupUpdater != null )
		{
			backupUpdater.aborted = true;
		}

		( (NotificationManager) getSystemService( NOTIFICATION_SERVICE ) ).cancel( NOTIFY_EXPORT_FINISHED );

		super.onStop( );
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

	private void loadApps( )
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
				final PackageManager pm = getPackageManager( );
				List<ApplicationInfo> allApps = pm.getInstalledApplications( 0 );

				final List<ApplicationInfo> filteredApps = filterApps( allApps );

				ArrayList<AppInfoHolder> dataList = new ArrayList<AppInfoHolder>( );

				for ( int i = 0, size = filteredApps.size( ); i < size; i++ )
				{
					ApplicationInfo info = filteredApps.get( i );

					AppInfoHolder holder = new AppInfoHolder( );
					holder.appInfo = info;

					try
					{
						PackageInfo pi = pm.getPackageInfo( info.packageName, 0 );

						holder.version = versionPrefix
								+ " " //$NON-NLS-1$
								+ ( pi.versionName == null ? String.valueOf( pi.versionCode )
										: pi.versionName );

						holder.versionCode = pi.versionCode;

						if ( info.sourceDir != null
								&& info.sourceDir.contains( "/data/app-private" ) ) //$NON-NLS-1$
						{
							holder.isPrivate = true;
						}
					}
					catch ( NameNotFoundException e )
					{
						Log.e( ApplicationManager.class.getName( ),
								e.getLocalizedMessage( ),
								e );
					}

					dataList.add( holder );
				}

				handler.sendMessage( handler.obtainMessage( MSG_UPDATE,
						dataList ) );
			}
		},
				"MainUpdater" ).start( ); //$NON-NLS-1$
	}

	List<ApplicationInfo> filterApps( List<ApplicationInfo> apps )
	{
		if ( apps == null || apps.size( ) == 0 )
		{
			return apps;
		}

		int type = Util.getIntOption( this,
				PREF_KEY_FILTER_APP_TYPE,
				APP_TYPE_ALL );

		if ( type == APP_TYPE_SYS )
		{
			List<ApplicationInfo> sysApps = new ArrayList<ApplicationInfo>( );

			for ( int i = 0, size = apps.size( ); i < size; i++ )
			{
				ApplicationInfo ai = apps.get( i );

				if ( ( ai.flags & ApplicationInfo.FLAG_SYSTEM ) != 0 )
				{
					sysApps.add( ai );
				}
			}

			return sysApps;
		}
		else if ( type == APP_TYPE_USER )
		{
			List<ApplicationInfo> userApps = new ArrayList<ApplicationInfo>( );

			for ( int i = 0, size = apps.size( ); i < size; i++ )
			{
				ApplicationInfo ai = apps.get( i );

				if ( ( ai.flags & ApplicationInfo.FLAG_SYSTEM ) == 0 )
				{
					userApps.add( ai );
				}
			}

			return userApps;
		}

		return apps;
	}

	private static boolean ensureSDCard( )
	{
		String state = Environment.getExternalStorageState( );

		return Environment.MEDIA_MOUNTED.equals( state );
	}

	private static List<AppInfoHolder> getSelected( ListView lstApps )
	{
		int count = lstApps.getCount( );

		ArrayList<AppInfoHolder> apps = new ArrayList<AppInfoHolder>( );

		for ( int i = 0; i < count; i++ )
		{
			AppInfoHolder holder = (AppInfoHolder) lstApps.getItemAtPosition( i );

			if ( holder.checked )
			{
				apps.add( holder );
			}
		}

		return apps;
	}

	int getSelectedCount( ListView lstApps )
	{
		int count = lstApps.getCount( );

		int s = 0;

		for ( int i = 0; i < count; i++ )
		{
			AppInfoHolder holder = (AppInfoHolder) lstApps.getItemAtPosition( i );

			if ( holder.checked )
			{
				s++;
			}
		}

		return s;
	}

	void export( final List<AppInfoHolder> apps )
	{
		if ( apps == null || apps.isEmpty( ) )
		{
			Util.shortToast( this, R.string.no_app_selected );
			return;
		}

		if ( progress == null )
		{
			progress = new ProgressDialog( this );
		}
		progress.setMessage( getResources( ).getString( R.string.start_exporting ) );
		progress.setIndeterminate( false );
		progress.setProgressStyle( ProgressDialog.STYLE_HORIZONTAL );
		progress.setMax( apps.size( ) );

		progress.show( );

		new Thread( new Runnable( ) {

			public void run( )
			{
				String exportFolder = Util.getStringOption( ApplicationManager.this,
						PREF_KEY_APP_EXPORT_DIR,
						DEFAULT_EXPORT_FOLDER );

				File output = new File( exportFolder );

				if ( !output.exists( ) )
				{
					if ( !output.mkdirs( ) )
					{
						handler.sendMessage( Message.obtain( handler,
								MSG_COPING_ERROR,
								0,
								0,
								getString( R.string.error_create_folder,
										output.getAbsolutePath( ) ) ) );

						return;
					}
				}

				File sysoutput = new File( output, SYS_APP );

				if ( !sysoutput.exists( ) )
				{
					if ( !sysoutput.mkdirs( ) )
					{
						handler.sendMessage( Message.obtain( handler,
								MSG_COPING_ERROR,
								0,
								0,
								getString( R.string.error_create_folder,
										sysoutput.getAbsolutePath( ) ) ) );

						return;
					}
				}

				File useroutput = new File( output, USER_APP );

				if ( !useroutput.exists( ) )
				{
					if ( !useroutput.mkdirs( ) )
					{
						handler.sendMessage( Message.obtain( handler,
								MSG_COPING_ERROR,
								0,
								0,
								getString( R.string.error_create_folder,
										useroutput.getAbsolutePath( ) ) ) );

						return;
					}
				}

				int skipped = 0;
				int succeed = 0;

				for ( int i = 0, size = apps.size( ); i < size; i++ )
				{
					ApplicationInfo app = apps.get( i ).appInfo;

					String src = app.sourceDir;

					if ( src != null )
					{
						File srcFile = new File( src );

						if ( src.contains( "/data/app-private" ) //$NON-NLS-1$
								|| !srcFile.canRead( ) )
						{
							skipped++;

							continue;
						}

						String appName = getFileName( src );

						if ( appName != null )
						{
							File targetOutput = useroutput;

							if ( ( app.flags & ApplicationInfo.FLAG_SYSTEM ) != 0 )
							{
								targetOutput = sysoutput;
							}

							File destFile = new File( targetOutput, appName );

							handler.sendMessage( Message.obtain( handler,
									MSG_COPING,
									appName ) );

							try
							{
								copyFile( srcFile, destFile );

								succeed++;
							}
							catch ( Exception e )
							{
								Log.e( ApplicationManager.class.getName( ),
										e.getLocalizedMessage( ),
										e );

								handler.sendMessage( Message.obtain( handler,
										MSG_COPING_ERROR,
										1,
										0,
										e.getLocalizedMessage( ) ) );

								continue;
							}
						}
					}
				}

				handler.sendMessage( Message.obtain( handler,
						MSG_COPING_FINISHED,
						succeed,
						skipped,
						apps ) );
			}
		} ).start( );
	}

	static String getFileName( String fullName )
	{
		if ( fullName == null )
		{
			return null;
		}

		int idx = fullName.lastIndexOf( '/' );
		if ( idx == -1 )
		{
			return fullName;
		}

		return fullName.substring( idx + 1 );
	}

	static void copyFile( File src, File dest ) throws IOException
	{
		InputStream fis = new BufferedInputStream( new FileInputStream( src ),
				8192 * 4 );
		OutputStream fos = new BufferedOutputStream( new FileOutputStream( dest ),
				8192 * 4 );

		byte[] buf = new byte[4096];

		int i;
		while ( ( i = fis.read( buf ) ) != -1 )
		{
			fos.write( buf, 0, i );
		}

		fis.close( );
		fos.close( );
	}

	@Override
	protected void onActivityResult( int requestCode, int resultCode,
			Intent data )
	{
		if ( requestCode == REQUEST_SETTINGS )
		{
			Util.updateStringOption( data, this, PREF_KEY_APP_EXPORT_DIR );

			Util.updateIntOption( data,
					this,
					PREF_KEY_FILTER_APP_TYPE,
					APP_TYPE_ALL );
			Util.updateIntOption( data,
					this,
					PREF_KEY_SORT_ORDER_TYPE,
					ORDER_TYPE_NAME );
			Util.updateIntOption( data,
					this,
					PREF_KEY_SORT_DIRECTION,
					ORDER_ASC );

			Util.updateBooleanOption( data, this, PREF_KEY_SHOW_SIZE );
			Util.updateBooleanOption( data, this, PREF_KEY_SHOW_DATE );
			Util.updateBooleanOption( data, this, PREF_KEY_SHOW_BACKUP_STATE );
		}
	}

	@Override
	public boolean onCreateOptionsMenu( Menu menu )
	{
		MenuItem mi = menu.add( Menu.NONE,
				MI_DELETE,
				Menu.NONE,
				R.string.uninstall );
		mi.setIcon( android.R.drawable.ic_menu_delete );

		mi = menu.add( Menu.NONE, MI_SHARE, Menu.NONE, R.string.share );
		mi.setIcon( android.R.drawable.ic_menu_share );

		mi = menu.add( Menu.NONE, MI_REVERT, Menu.NONE, R.string.restore );
		mi.setIcon( android.R.drawable.ic_menu_revert );

		mi = menu.add( Menu.NONE,
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
			Intent it = new Intent( this, AppSettings.class );

			it.putExtra( PREF_KEY_FILTER_APP_TYPE, Util.getIntOption( this,
					PREF_KEY_FILTER_APP_TYPE,
					APP_TYPE_ALL ) );
			it.putExtra( PREF_KEY_APP_EXPORT_DIR, Util.getStringOption( this,
					PREF_KEY_APP_EXPORT_DIR,
					DEFAULT_EXPORT_FOLDER ) );
			it.putExtra( PREF_KEY_SORT_ORDER_TYPE, Util.getIntOption( this,
					PREF_KEY_SORT_ORDER_TYPE,
					ORDER_TYPE_NAME ) );
			it.putExtra( PREF_KEY_SORT_DIRECTION,
					Util.getIntOption( this, PREF_KEY_SORT_DIRECTION, ORDER_ASC ) );
			it.putExtra( PREF_KEY_SHOW_SIZE,
					Util.getBooleanOption( this, PREF_KEY_SHOW_SIZE ) );
			it.putExtra( PREF_KEY_SHOW_DATE,
					Util.getBooleanOption( this, PREF_KEY_SHOW_DATE ) );
			it.putExtra( PREF_KEY_SHOW_BACKUP_STATE,
					Util.getBooleanOption( this, PREF_KEY_SHOW_BACKUP_STATE ) );

			startActivityForResult( it, REQUEST_SETTINGS );

			return true;
		}
		else if ( item.getItemId( ) == MI_REVERT )
		{
			Intent it = new Intent( this, RestoreAppActivity.class );

			it.putExtra( KEY_RESTORE_PATH,
					new File( Util.getStringOption( this,
							PREF_KEY_APP_EXPORT_DIR,
							DEFAULT_EXPORT_FOLDER ), USER_APP ).getAbsolutePath( ) );

			startActivityForResult( it, REQUEST_RESTORE );

			return true;
		}
		else if ( item.getItemId( ) == MI_SHARE )
		{
			doShare( );

			return true;
		}
		else if ( item.getItemId( ) == MI_DELETE )
		{
			doUninstall( );

			return true;
		}

		return false;
	}

	@Override
	public void onCreateContextMenu( ContextMenu menu, View v,
			ContextMenuInfo menuInfo )
	{
		menu.setHeaderTitle( R.string.actions );
		menu.add( Menu.NONE, MI_LAUNCH, MI_LAUNCH, R.string.run );
		menu.add( Menu.NONE, MI_SEARCH, MI_SEARCH, R.string.search_market );
		menu.add( Menu.NONE, MI_DETAILS, MI_DETAILS, R.string.details );
	}

	@Override
	public boolean onContextItemSelected( MenuItem item )
	{
		int pos = ( (AdapterContextMenuInfo) item.getMenuInfo( ) ).position;

		if ( pos >= 0 && pos < getListView( ).getCount( ) )
		{
			final AppInfoHolder ai = (AppInfoHolder) getListView( ).getItemAtPosition( pos );

			final String pkgName = ai.appInfo.packageName;

			if ( item.getItemId( ) == MI_LAUNCH )
			{
				if ( !pkgName.equals( this.getPackageName( ) ) )
				{
					Intent it = new Intent( "android.intent.action.MAIN" ); //$NON-NLS-1$
					it.addCategory( Intent.CATEGORY_LAUNCHER );

					List<ResolveInfo> acts = getPackageManager( ).queryIntentActivities( it,
							0 );

					if ( acts != null )
					{
						boolean started = false;

						for ( int i = 0, size = acts.size( ); i < size; i++ )
						{
							ResolveInfo ri = acts.get( i );

							if ( pkgName.equals( ri.activityInfo.packageName ) )
							{
								it.setClassName( ri.activityInfo.packageName,
										ri.activityInfo.name );

								it.addFlags( Intent.FLAG_ACTIVITY_NEW_TASK )
										.addFlags( Intent.FLAG_ACTIVITY_CLEAR_TOP );

								startActivity( it );

								started = true;
								break;
							}
						}

						if ( !started )
						{
							Util.shortToast( this, R.string.run_failed );
						}
					}
				}

				return true;
			}
			else if ( item.getItemId( ) == MI_SEARCH )
			{
				Intent it = new Intent( Intent.ACTION_VIEW );

				it.setData( Uri.parse( "market://search?q=pname:" + pkgName ) ); //$NON-NLS-1$

				it = Intent.createChooser( it, null );

				startActivity( it );

				return true;
			}
			else if ( item.getItemId( ) == MI_DETAILS )
			{
				ApplicationInfo appInfo = ai.appInfo;

				StringBuffer sb = new StringBuffer( ).append( "<small>" ) //$NON-NLS-1$
						.append( getString( R.string.pkg_name ) )
						.append( ": " ) //$NON-NLS-1$
						.append( appInfo.packageName )
						.append( "<br>" ) //$NON-NLS-1$
						.append( getString( R.string.target_sdk ) )
						.append( ": " ) //$NON-NLS-1$
						.append( Util.getTargetSdkVersion( this, appInfo ) )
						.append( "<br>" ) //$NON-NLS-1$
						.append( getString( R.string.uid ) )
						.append( ": " ) //$NON-NLS-1$
						.append( appInfo.uid )
						.append( "<br>" ) //$NON-NLS-1$
						.append( getString( R.string.public_source ) )
						.append( ": " ) //$NON-NLS-1$
						.append( appInfo.publicSourceDir )
						.append( "<br>" ) //$NON-NLS-1$
						.append( getString( R.string.source ) )
						.append( ": " ) //$NON-NLS-1$
						.append( appInfo.sourceDir )
						.append( "<br>" ) //$NON-NLS-1$
						.append( getString( R.string.data ) )
						.append( ": " ) //$NON-NLS-1$
						.append( appInfo.dataDir )
						.append( "<br>" ) //$NON-NLS-1$
						.append( getString( R.string.process ) )
						.append( ": " ) //$NON-NLS-1$
						.append( appInfo.processName )
						.append( "<br>" ) //$NON-NLS-1$
						.append( getString( R.string.app_class ) )
						.append( ": " ) //$NON-NLS-1$
						.append( appInfo.className == null ? "" //$NON-NLS-1$
								: appInfo.className )
						.append( "<br>" ) //$NON-NLS-1$
						.append( getString( R.string.task_affinity ) )
						.append( ": " ) //$NON-NLS-1$
						.append( appInfo.taskAffinity )
						.append( "<br>" ) //$NON-NLS-1$
						.append( getString( R.string.permission ) )
						.append( ": " ) //$NON-NLS-1$
						.append( appInfo.permission == null ? "" //$NON-NLS-1$
								: appInfo.permission )
						.append( "<br>" ) //$NON-NLS-1$
						.append( getString( R.string.flags ) )
						.append( ": " ) //$NON-NLS-1$
						.append( appInfo.flags )
						.append( "<br>" ) //$NON-NLS-1$
						.append( getString( R.string.enabled ) )
						.append( ": " ) //$NON-NLS-1$
						.append( appInfo.enabled )
						.append( "<br>" ) //$NON-NLS-1$
						.append( getString( R.string.manage_space_ac ) )
						.append( ": " ) //$NON-NLS-1$
						.append( appInfo.manageSpaceActivityName == null ? "" //$NON-NLS-1$
								: appInfo.manageSpaceActivityName )
						.append( "</small>" ); //$NON-NLS-1$

				new AlertDialog.Builder( this ).setTitle( ai.label == null ? appInfo.packageName
						: ai.label )
						.setNeutralButton( R.string.close, null )
						.setMessage( Html.fromHtml( sb.toString( ) ) )
						.create( )
						.show( );

				return true;
			}
		}

		return false;
	}

	private void doUninstall( )
	{
		final List<AppInfoHolder> sels = getSelected( getListView( ) );

		if ( sels == null || sels.size( ) == 0 )
		{
			Util.shortToast( this, R.string.no_app_selected );
		}
		else
		{
			OnClickListener listener = new OnClickListener( ) {

				public void onClick( DialogInterface dialog, int which )
				{
					boolean canUninstall = false;

					for ( int i = 0, size = sels.size( ); i < size; i++ )
					{
						ApplicationInfo app = sels.get( i ).appInfo;

						Intent it = new Intent( Intent.ACTION_DELETE,
								Uri.parse( "package:" //$NON-NLS-1$
										+ app.packageName ) );

						if ( !canUninstall )
						{
							List<ResolveInfo> acts = getPackageManager( ).queryIntentActivities( it,
									0 );

							canUninstall = acts.size( ) > 0;
						}

						if ( canUninstall )
						{
							startActivity( it );
						}
					}

					if ( !canUninstall )
					{
						Util.shortToast( ApplicationManager.this,
								R.string.uninstall_fail );

						Log.d( ApplicationManager.class.getName( ),
								"No activity found to handle the uninstall request." ); //$NON-NLS-1$
					}
				}
			};

			new AlertDialog.Builder( this ).setTitle( R.string.warning )
					.setMessage( R.string.uninstall_msg )
					.setPositiveButton( android.R.string.ok, listener )
					.setNegativeButton( android.R.string.cancel, null )
					.create( )
					.show( );
		}
	}

	private void doShare( )
	{
		final List<AppInfoHolder> sels = getSelected( getListView( ) );

		if ( sels == null || sels.size( ) == 0 )
		{
			Util.shortToast( this, R.string.no_app_selected );
		}
		else
		{
			final boolean[] items = new boolean[]{
					true, true, true, true
			};

			OnMultiChoiceClickListener selListener = new OnMultiChoiceClickListener( ) {

				public void onClick( DialogInterface dialog, int which,
						boolean isChecked )
				{
					items[which] = isChecked;
				}
			};

			OnClickListener sendListener = new OnClickListener( ) {

				public void onClick( DialogInterface dialog, int which )
				{
					sendContent( items, sels );
				}
			};

			new AlertDialog.Builder( this ).setTitle( R.string.include )
					.setMultiChoiceItems( new CharSequence[]{
							getString( R.string.version ),
							getString( R.string.target_sdk ),
							getString( R.string.pkg_name ),
							getString( R.string.market_link ),
					},
							items,
							selListener )
					.setPositiveButton( android.R.string.ok, sendListener )
					.setNegativeButton( android.R.string.cancel, null )
					.create( )
					.show( );
		}
	}

	void sendContent( final boolean[] items, final List<AppInfoHolder> apps )
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
				String content = collectTextContent( items, apps );

				if ( aborted )
				{
					return;
				}

				if ( content != null )
				{
					handler.sendMessage( handler.obtainMessage( MSG_CHECK_FORCE_COMPRESSION,
							SysInfoManager.PLAINTEXT,
							0,
							content ) );
				}
				else
				{
					handler.sendMessage( handler.obtainMessage( MSG_CONTENT_READY,
							SysInfoManager.PLAINTEXT,
							0,
							content ) );
				}
			}
		} ).start( );
	}

	String collectTextContent( boolean[] items, List<AppInfoHolder> apps )
	{
		StringBuilder sb = new StringBuilder( );

		for ( int i = 0, size = apps.size( ); i < size; i++ )
		{
			AppInfoHolder ai = apps.get( i );

			if ( i > 0 )
			{
				sb.append( '\n' );
			}

			sb.append( ai.label == null ? ai.appInfo.packageName : ai.label );

			if ( items[0] )
			{
				sb.append( ", " + ai.version ); //$NON-NLS-1$
			}

			if ( items[1] )
			{
				sb.append( ", SDK " + Util.getTargetSdkVersion( this, ai.appInfo ) ); //$NON-NLS-1$
			}

			if ( items[2] )
			{
				sb.append( ", " + ai.appInfo.packageName ); //$NON-NLS-1$
			}

			if ( items[3] )
			{
				sb.append( ", http://market.android.com/search?q=pname:" //$NON-NLS-1$
						+ ai.appInfo.packageName );
			}
		}

		if ( sb.length( ) > 0 )
		{
			return sb.toString( );
		}

		return null;
	}

	void doExport( )
	{
		final List<AppInfoHolder> sels = getSelected( getListView( ) );

		if ( sels == null || sels.size( ) == 0 )
		{
			Util.shortToast( this, R.string.no_app_selected );
		}
		else if ( !ensureSDCard( ) )
		{
			Util.shortToast( this, R.string.error_sdcard );
		}
		else
		{
			OnClickListener listener = new OnClickListener( ) {

				public void onClick( DialogInterface dialog, int which )
				{
					if ( which == Dialog.BUTTON_POSITIVE )
					{
						export( sels );
					}
				}
			};

			new AlertDialog.Builder( this ).setTitle( R.string.warning )
					.setMessage( getString( R.string.warning_msg,
							Util.getStringOption( this,
									PREF_KEY_APP_EXPORT_DIR,
									DEFAULT_EXPORT_FOLDER ) ) )
					.setPositiveButton( R.string.cont, listener )
					.setNegativeButton( android.R.string.cancel, listener )
					.create( )
					.show( );
		}
	}

	void toggleAllSelection( boolean selected )
	{
		ListView lstApps = getListView( );

		int totalCount = lstApps.getCount( );

		for ( int i = 0; i < totalCount; i++ )
		{
			AppInfoHolder holder = (AppInfoHolder) lstApps.getItemAtPosition( i );

			holder.checked = selected;
		}

		if ( !selected )
		{
			hideButtons( );
		}

		( (ArrayAdapter) lstApps.getAdapter( ) ).notifyDataSetChanged( );
	}

	void hideButtons( )
	{
		View v = findViewById( R.id.app_footer );

		if ( v.getVisibility( ) != View.GONE )
		{
			v.setVisibility( View.GONE );

			v.startAnimation( AnimationUtils.loadAnimation( ApplicationManager.this,
					R.anim.footer_disappear ) );
		}
	}

	/**
	 * PackageSizeObserver
	 */
	private static final class PkgSizeObserver extends
			IPackageStatsObserver.Stub
	{

		private CountDownLatch count;
		private Activity ac;
		private AppCache appCache;

		PkgSizeObserver( CountDownLatch count, Activity ac, AppCache appCache )
		{
			this.count = count;
			this.ac = ac;
			this.appCache = appCache;
		}

		void invokeGetPkgSize( String pkgName, PackageManager pm )
		{
			if ( mdGetPackageSizeInfo != null )
			{
				try
				{
					mdGetPackageSizeInfo.invoke( pm, pkgName, this );
				}
				catch ( Exception e )
				{
					Log.e( ApplicationManager.class.getName( ),
							e.getLocalizedMessage( ),
							e );
				}
			}
		}

		public void onGetStatsCompleted( PackageStats pStats, boolean succeeded )
				throws RemoteException
		{
			AppInfoHolder holder = appCache.appLookup.get( pStats.packageName );

			if ( holder != null )
			{
				holder.size = Formatter.formatFileSize( ac, pStats.codeSize )
						+ " + " //$NON-NLS-1$
						+ Formatter.formatFileSize( ac, pStats.dataSize )
						+ " (" //$NON-NLS-1$
						+ Formatter.formatFileSize( ac, pStats.cacheSize )
						+ ')';

				holder.codeSize = pStats.codeSize;
				holder.dataSize = pStats.dataSize;
				holder.cacheSize = pStats.cacheSize;
			}

			count.countDown( );
		}

	}

	/**
	 * PkgSizeUpdaterThread
	 */
	private static final class PkgSizeUpdaterThread extends Thread
	{

		private Activity ac;
		private AppCache appCache;
		private Handler handler;

		volatile boolean aborted;

		PkgSizeUpdaterThread( Activity ac, AppCache appCache, Handler handler )
		{
			super( "SizeUpdater" ); //$NON-NLS-1$

			this.ac = ac;
			this.appCache = appCache;
			this.handler = handler;
		}

		@Override
		public void run( )
		{
			PackageManager pm = ac.getPackageManager( );

			ArrayList<AppInfoHolder> localList = appCache.generateLocalList( );

			int totalSize = localList.size( );
			int secSize = 32;

			int num = totalSize / secSize;
			if ( num * secSize < totalSize )
			{
				num++;
			}

			for ( int k = 0; k < num; k++ )
			{
				int secCount = ( k + 1 ) * secSize > totalSize ? ( totalSize - k
						* secSize )
						: secSize;

				CountDownLatch count = new CountDownLatch( secCount );

				PkgSizeObserver observer = new PkgSizeObserver( count,
						ac,
						appCache );

				for ( int i = 0; i < secCount; i++ )
				{
					if ( aborted )
					{
						return;
					}

					observer.invokeGetPkgSize( localList.get( k * secSize + i ).appInfo.packageName,
							pm );
				}

				try
				{
					count.await( );

					if ( k == num - 1 )
					{
						int type = Util.getIntOption( ac,
								PREF_KEY_SORT_ORDER_TYPE,
								ORDER_TYPE_NAME );

						if ( type == ORDER_TYPE_CODE_SIZE
								|| type == ORDER_TYPE_DATA_SIZE
								|| type == ORDER_TYPE_CACHE_SIZE
								|| type == ORDER_TYPE_TOTAL_SIZE )
						{
							appCache.reOrder( type, Util.getIntOption( ac,
									PREF_KEY_SORT_DIRECTION,
									ORDER_ASC ) );

							handler.sendMessage( handler.obtainMessage( MSG_REFRESH_PKG_SIZE,
									1,
									0 ) );

							return;
						}
					}

					handler.sendMessage( handler.obtainMessage( MSG_REFRESH_PKG_SIZE,
							0,
							0 ) );
				}
				catch ( InterruptedException e )
				{
					Log.e( ApplicationManager.class.getName( ),
							e.getLocalizedMessage( ),
							e );
				}
			}
		}
	}

	/**
	 * ResourceUpdaterThread
	 */
	private static final class ResourceUpdaterThread extends Thread
	{

		private Activity ac;
		private AppCache appCache;
		private Handler handler;

		volatile boolean aborted;

		ResourceUpdaterThread( Activity ac, AppCache appCache, Handler handler )
		{
			super( "ResourceUpdater" ); //$NON-NLS-1$

			this.ac = ac;
			this.appCache = appCache;
			this.handler = handler;
		}

		public void run( )
		{
			ApplicationInfo ai;
			AppInfoHolder holder;

			PackageManager pm = ac.getPackageManager( );

			ArrayList<AppInfoHolder> localList = appCache.generateLocalList( );

			for ( int i = 0, size = localList.size( ); i < size; i++ )
			{
				if ( aborted )
				{
					return;
				}

				ai = localList.get( i ).appInfo;

				CharSequence label = ai.loadLabel( pm );

				holder = appCache.appLookup.get( ai.packageName );

				if ( holder != null )
				{
					holder.label = label;
				}
			}

			// reorder by new names
			if ( Util.getIntOption( ac,
					PREF_KEY_SORT_ORDER_TYPE,
					ORDER_TYPE_NAME ) == ORDER_TYPE_NAME )
			{
				appCache.reOrder( ORDER_TYPE_NAME, Util.getIntOption( ac,
						PREF_KEY_SORT_DIRECTION,
						ORDER_ASC ) );

				handler.sendMessage( handler.obtainMessage( MSG_REFRESH_PKG_LABEL,
						1,
						0 ) );
			}
			else
			{
				handler.sendMessage( handler.obtainMessage( MSG_REFRESH_PKG_LABEL,
						0,
						0 ) );
			}

			for ( int i = 0, size = localList.size( ); i < size; i++ )
			{
				if ( aborted )
				{
					return;
				}

				ai = localList.get( i ).appInfo;

				try
				{
					Drawable icon = ai.loadIcon( pm );

					holder = appCache.appLookup.get( ai.packageName );

					if ( holder != null )
					{
						holder.icon = icon;
					}
				}
				catch ( OutOfMemoryError oom )
				{
					Log.e( ApplicationManager.class.getName( ),
							"OOM when loading icon: " //$NON-NLS-1$
									+ ai.packageName,
							oom );
				}
			}

			handler.sendEmptyMessage( MSG_REFRESH_PKG_ICON );
		}
	}

	/**
	 * BackupStateUpdaterThread
	 */
	private static final class BackupStateUpdaterThread extends Thread
	{

		private Activity ac;
		private List<AppInfoHolder> apps;
		private AppCache appCache;
		private Handler handler;

		volatile boolean aborted;

		BackupStateUpdaterThread( Activity ac, List<AppInfoHolder> apps,
				AppCache appCache, Handler handler )
		{
			super( "BackupStateUpdater" ); //$NON-NLS-1$

			this.ac = ac;
			this.apps = apps;
			this.appCache = appCache;
			this.handler = handler;
		}

		public void run( )
		{
			if ( apps == null )
			{
				apps = appCache.generateLocalList( );
			}

			if ( apps == null || apps.size( ) == 0 )
			{
				return;
			}

			String exportFolder = Util.getStringOption( ac,
					PREF_KEY_APP_EXPORT_DIR,
					DEFAULT_EXPORT_FOLDER );

			File sysoutput = null;
			File useroutput = null;

			File output = new File( exportFolder );

			if ( output.exists( ) )
			{
				sysoutput = new File( output, SYS_APP );

				if ( !sysoutput.exists( ) )
				{
					sysoutput = null;
				}

				useroutput = new File( output, USER_APP );

				if ( !useroutput.exists( ) )
				{
					useroutput = null;
				}
			}

			ApplicationInfo ai;
			AppInfoHolder holder;
			PackageInfo pi;

			PackageManager pm = ac.getPackageManager( );

			for ( int i = 0, size = apps.size( ); i < size; i++ )
			{
				if ( aborted )
				{
					return;
				}

				ai = apps.get( i ).appInfo;

				holder = appCache.appLookup.get( ai.packageName );

				if ( holder != null )
				{
					File targetOutput = useroutput;

					if ( ( ai.flags & ApplicationInfo.FLAG_SYSTEM ) != 0 )
					{
						targetOutput = sysoutput;
					}

					if ( targetOutput != null )
					{
						String src = ai.sourceDir;

						if ( src != null )
						{
							String appName = getFileName( src );

							if ( appName != null )
							{
								File destFile = new File( targetOutput, appName );

								if ( destFile.exists( ) )
								{
									pi = pm.getPackageArchiveInfo( destFile.getAbsolutePath( ),
											0 );

									if ( pi != null )
									{
										if ( pi.versionCode < holder.versionCode )
										{
											holder.backupState = 1;
										}
										else if ( pi.versionCode == holder.versionCode )
										{
											holder.backupState = 2;
										}
										else
										{
											holder.backupState = 3;
										}

										continue;
									}
								}
							}
						}
					}

					holder.backupState = 0;
				}
			}

			// reorder by backup state
			if ( Util.getIntOption( ac,
					PREF_KEY_SORT_ORDER_TYPE,
					ORDER_TYPE_NAME ) == ORDER_TYPE_BACKUP_STATE )
			{
				appCache.reOrder( ORDER_TYPE_BACKUP_STATE,
						Util.getIntOption( ac,
								PREF_KEY_SORT_DIRECTION,
								ORDER_ASC ) );

				handler.sendMessage( handler.obtainMessage( MSG_REFRESH_BACKUP_STATE,
						1,
						0 ) );
			}
			else
			{
				handler.sendMessage( handler.obtainMessage( MSG_REFRESH_BACKUP_STATE,
						0,
						0 ) );
			}
		}
	}

	/**
	 * AppInfoHolder
	 */
	private static final class AppInfoHolder
	{

		ApplicationInfo appInfo;

		CharSequence label;

		CharSequence version;

		Drawable icon;

		String size;

		long codeSize, dataSize, cacheSize;

		int backupState;

		int versionCode;

		boolean isPrivate;

		boolean checked;

		AppInfoHolder( )
		{

		}

		@Override
		public boolean equals( Object o )
		{
			if ( !( o instanceof AppInfoHolder ) )
			{
				return false;
			}

			AppInfoHolder that = (AppInfoHolder) o;

			return this.appInfo.packageName.equals( that.appInfo.packageName );
		}
	}

	/**
	 * NameComparator
	 */
	private static final class NameComparator implements
			Comparator<AppInfoHolder>
	{

		Collator clt = Collator.getInstance( );
		int direction;

		NameComparator( int direction )
		{
			this.direction = direction;
		}

		public int compare( AppInfoHolder obj1, AppInfoHolder obj2 )
		{
			String s1 = obj1.label == null ? obj1.appInfo.packageName
					: obj1.label.toString( );
			String s2 = obj2.label == null ? obj2.appInfo.packageName
					: obj2.label.toString( );

			return clt.compare( s1, s2 ) * direction;

		}
	}

	/**
	 * SizeComparator
	 */
	private static final class SizeComparator implements
			Comparator<AppInfoHolder>
	{

		int type;
		int direction;

		SizeComparator( int type, int direction )
		{
			this.type = type;
			this.direction = direction;
		}

		public int compare( AppInfoHolder obj1, AppInfoHolder obj2 )
		{
			switch ( type )
			{
				case ORDER_TYPE_CODE_SIZE :
					return ( obj1.codeSize == obj2.codeSize ? 0
							: ( obj1.codeSize < obj2.codeSize ? -1 : 1 ) )
							* direction;
				case ORDER_TYPE_DATA_SIZE :
					return ( obj1.dataSize == obj2.dataSize ? 0
							: ( obj1.dataSize < obj2.dataSize ? -1 : 1 ) )
							* direction;
				case ORDER_TYPE_CACHE_SIZE :
					return ( obj1.cacheSize == obj2.cacheSize ? 0
							: ( obj1.cacheSize < obj2.cacheSize ? -1 : 1 ) )
							* direction;
				case ORDER_TYPE_TOTAL_SIZE :
					long s1 = obj1.codeSize + obj1.dataSize + obj1.cacheSize;
					long s2 = obj2.codeSize + obj2.dataSize + obj2.cacheSize;
					return ( s1 == s2 ? 0 : ( s1 < s2 ? -1 : 1 ) ) * direction;
			}

			return 0;
		}
	}

	/**
	 * AppCache
	 */
	private static final class AppCache
	{

		ArrayList<AppInfoHolder> appList;

		HashMap<String, AppInfoHolder> appLookup;

		AppCache( )
		{
			appList = new ArrayList<AppInfoHolder>( );
			appLookup = new HashMap<String, AppInfoHolder>( );
		}

		synchronized ArrayList<AppInfoHolder> generateLocalList( )
		{
			ArrayList<AppInfoHolder> local = new ArrayList<AppInfoHolder>( );

			local.addAll( appList );

			return local;
		}

		synchronized void update( ArrayList<AppInfoHolder> apps )
		{
			appList.retainAll( apps );

			for ( int i = 0, size = apps.size( ); i < size; i++ )
			{
				AppInfoHolder ai = apps.get( i );

				AppInfoHolder oai = appLookup.get( ai.appInfo.packageName );

				if ( oai == null )
				{
					oai = ai;

					appLookup.put( ai.appInfo.packageName, ai );
				}
				else
				{
					oai.appInfo = ai.appInfo;
					oai.version = ai.version;
					oai.isPrivate = ai.isPrivate;
					oai.checked = ai.checked;
					oai.versionCode = ai.versionCode;
				}

				if ( !appList.contains( oai ) )
				{
					appList.add( oai );
				}
			}
		}

		synchronized void reOrder( int type, final int direction )
		{
			switch ( type )
			{
				case ORDER_TYPE_NAME :
					Collections.sort( appList, new NameComparator( direction ) );
					break;
				case ORDER_TYPE_CODE_SIZE :
				case ORDER_TYPE_DATA_SIZE :
				case ORDER_TYPE_CACHE_SIZE :
				case ORDER_TYPE_TOTAL_SIZE :
					Collections.sort( appList, new SizeComparator( type,
							direction ) );
					break;
				case ORDER_TYPE_INSTALL_DATE :
					Collections.sort( appList,
							new Comparator<AppInfoHolder>( ) {

								public int compare( AppInfoHolder obj1,
										AppInfoHolder obj2 )
								{
									long d1 = 0;
									long d2 = 0;

									if ( obj1.appInfo.sourceDir != null )
									{
										d1 = new File( obj1.appInfo.sourceDir ).lastModified( );
									}
									if ( obj2.appInfo.sourceDir != null )
									{
										d2 = new File( obj2.appInfo.sourceDir ).lastModified( );
									}

									return ( d1 == d2 ? 0 : ( d1 < d2 ? -1 : 1 ) )
											* direction;
								}
							} );
					break;
				case ORDER_TYPE_BACKUP_STATE :
					Collections.sort( appList,
							new Comparator<AppInfoHolder>( ) {

								public int compare( AppInfoHolder obj1,
										AppInfoHolder obj2 )
								{
									return ( obj1.backupState - obj2.backupState )
											* direction;
								}
							} );
					break;
			}
		}
	}

	/**
	 * AppSettings
	 */
	public static final class AppSettings extends PreferenceActivity
	{

		@Override
		protected void onCreate( Bundle savedInstanceState )
		{
			requestWindowFeature( Window.FEATURE_NO_TITLE );

			super.onCreate( savedInstanceState );

			addPreferencesFromResource( R.xml.app_pref );

			refreshBackupFolder( );
			refreshAppType( );
			refreshSortType( );
			refreshSortDirection( );
			refreshBooleanOption( PREF_KEY_SHOW_SIZE );
			refreshBooleanOption( PREF_KEY_SHOW_DATE );
			refreshBooleanOption( PREF_KEY_SHOW_BACKUP_STATE );

			setResult( RESULT_OK, getIntent( ) );
		}

		void refreshBooleanOption( String key )
		{
			boolean val = getIntent( ).getBooleanExtra( key, true );

			( (CheckBoxPreference) findPreference( key ) ).setChecked( val );
		}

		void refreshBackupFolder( )
		{
			findPreference( "export_dir" ).setSummary( getIntent( ).getStringExtra( PREF_KEY_APP_EXPORT_DIR ) ); //$NON-NLS-1$
		}

		void refreshAppType( )
		{
			int type = getIntent( ).getIntExtra( PREF_KEY_FILTER_APP_TYPE,
					APP_TYPE_ALL );

			int res = R.string.all_apps;
			if ( type == APP_TYPE_SYS )
			{
				res = R.string.sys_apps;
			}
			else if ( type == APP_TYPE_USER )
			{
				res = R.string.user_apps;
			}

			findPreference( "app_filter" ).setSummary( res ); //$NON-NLS-1$
		}

		void refreshSortType( )
		{
			int type = getIntent( ).getIntExtra( PREF_KEY_SORT_ORDER_TYPE,
					ORDER_TYPE_NAME );

			String label = null;
			switch ( type )
			{
				case ORDER_TYPE_NAME :
					label = getString( R.string.name );
					break;
				case ORDER_TYPE_CODE_SIZE :
					label = getString( R.string.code_size );
					break;
				case ORDER_TYPE_DATA_SIZE :
					label = getString( R.string.data_size );
					break;
				case ORDER_TYPE_CACHE_SIZE :
					label = getString( R.string.cache_size );
					break;
				case ORDER_TYPE_TOTAL_SIZE :
					label = getString( R.string.total_size );
					break;
				case ORDER_TYPE_INSTALL_DATE :
					label = getString( R.string.installed_date );
					break;
				case ORDER_TYPE_BACKUP_STATE :
					label = getString( R.string.backup_state );
			}

			findPreference( "sort_type" ).setSummary( label ); //$NON-NLS-1$
		}

		void refreshSortDirection( )
		{
			int type = getIntent( ).getIntExtra( PREF_KEY_SORT_DIRECTION,
					ORDER_ASC );

			String label = type == ORDER_ASC ? getString( R.string.ascending )
					: getString( R.string.descending );

			findPreference( "sort_direction" ).setSummary( label ); //$NON-NLS-1$
		}

		@Override
		public boolean onPreferenceTreeClick(
				PreferenceScreen preferenceScreen, Preference preference )
		{
			final Intent it = getIntent( );

			if ( "export_dir".equals( preference.getKey( ) ) ) //$NON-NLS-1$
			{
				final EditText txt = new EditText( this );
				txt.setText( it.getStringExtra( PREF_KEY_APP_EXPORT_DIR ) );

				OnClickListener listener = new OnClickListener( ) {

					public void onClick( DialogInterface dialog, int which )
					{
						String path = txt.getText( ).toString( );

						if ( path != null )
						{
							path = path.trim( );

							if ( path.length( ) == 0 )
							{
								path = null;
							}
						}

						if ( path == null )
						{
							path = DEFAULT_EXPORT_FOLDER;
						}

						it.putExtra( PREF_KEY_APP_EXPORT_DIR, path );

						dialog.dismiss( );

						refreshBackupFolder( );
					}
				};

				new AlertDialog.Builder( this ).setTitle( R.string.export_dir )
						.setPositiveButton( android.R.string.ok, listener )
						.setNegativeButton( android.R.string.cancel, null )
						.setView( txt )
						.create( )
						.show( );

				return true;
			}
			else if ( "app_filter".equals( preference.getKey( ) ) ) //$NON-NLS-1$
			{
				OnClickListener listener = new OnClickListener( ) {

					public void onClick( DialogInterface dialog, int which )
					{
						it.putExtra( PREF_KEY_FILTER_APP_TYPE, which );

						dialog.dismiss( );

						refreshAppType( );
					}
				};

				new AlertDialog.Builder( this ).setTitle( R.string.filter_title )
						.setNeutralButton( R.string.close, null )
						.setSingleChoiceItems( new CharSequence[]{
								getText( R.string.all_apps ),
								getText( R.string.sys_apps ),
								getText( R.string.user_apps )
						},
								it.getIntExtra( PREF_KEY_FILTER_APP_TYPE,
										APP_TYPE_ALL ),
								listener )
						.create( )
						.show( );

				return true;
			}
			else if ( PREF_KEY_SHOW_SIZE.equals( preference.getKey( ) ) )
			{
				it.putExtra( PREF_KEY_SHOW_SIZE,
						( (CheckBoxPreference) findPreference( PREF_KEY_SHOW_SIZE ) ).isChecked( ) );

				return true;
			}
			else if ( PREF_KEY_SHOW_DATE.equals( preference.getKey( ) ) )
			{
				it.putExtra( PREF_KEY_SHOW_DATE,
						( (CheckBoxPreference) findPreference( PREF_KEY_SHOW_DATE ) ).isChecked( ) );

				return true;
			}
			else if ( PREF_KEY_SHOW_BACKUP_STATE.equals( preference.getKey( ) ) )
			{
				it.putExtra( PREF_KEY_SHOW_BACKUP_STATE,
						( (CheckBoxPreference) findPreference( PREF_KEY_SHOW_BACKUP_STATE ) ).isChecked( ) );

				return true;
			}
			else if ( "sort_type".equals( preference.getKey( ) ) ) //$NON-NLS-1$
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
								getString( R.string.name ),
								getString( R.string.code_size ),
								getString( R.string.data_size ),
								getString( R.string.cache_size ),
								getString( R.string.total_size ),
								getString( R.string.installed_date ),
								getString( R.string.backup_state ),
						},
								it.getIntExtra( PREF_KEY_SORT_ORDER_TYPE,
										ORDER_TYPE_NAME ),
								listener )
						.create( )
						.show( );

				return true;
			}
			else if ( "sort_direction".equals( preference.getKey( ) ) ) //$NON-NLS-1$
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
}
