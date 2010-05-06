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
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.CountDownLatch;

import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ListActivity;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.DialogInterface.OnClickListener;
import android.content.pm.ApplicationInfo;
import android.content.pm.IPackageStatsObserver;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageStats;
import android.content.pm.ResolveInfo;
import android.content.pm.PackageManager.NameNotFoundException;
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
import android.text.format.Formatter;
import android.util.Log;
import android.view.ContextMenu;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.animation.AnimationUtils;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.AdapterView.AdapterContextMenuInfo;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.CompoundButton.OnCheckedChangeListener;

/**
 * ApplicationManager
 */
public final class ApplicationManager extends ListActivity
{

	private static final int MI_LAUNCH = 1;
	private static final int MI_SEARCH = 2;

	private static final int MSG_COPING = 1;
	private static final int MSG_COPING_ERROR = 2;
	private static final int MSG_COPING_FINISHED = 3;
	private static final int MSG_INIT_OK = 9;
	private static final int MSG_DISMISS_PROGRESS = 10;
	private static final int MSG_REFRESH_PKG_SIZE = 11;
	private static final int MSG_REFRESH_PKG_LABEL = 12;
	private static final int MSG_REFRESH_PKG_ICON = 13;
	private static final int MSG_REFRESH_BACKUP_STATE = 14;

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

	private static final int ORDER_ASC = 1;
	private static final int ORDER_DESC = -1;

	private static final int REQUEST_SETTINGS = 1;
	private static final int REQUEST_RESTORE = 2;

	private static final String PREF_KEY_FILTER_APP_TYPE = "filter_app_type"; //$NON-NLS-1$
	private static final String PREF_KEY_APP_EXPORT_DIR = "app_export_dir"; //$NON-NLS-1$
	private static final String PREF_KEY_SORT_ORDER_TYPE = "sort_order_type"; //$NON-NLS-1$
	private static final String PREF_KEY_SORT_DIRECTION = "sort_direction"; //$NON-NLS-1$
	private static final String PREF_KEY_SHOW_SIZE = "show_size"; //$NON-NLS-1$
	private static final String PREF_KEY_SHOW_DATE = "show_date"; //$NON-NLS-1$
	private static final String PREF_KEY_SHOW_BACKUP_STATE = "show_backup_state"; //$NON-NLS-1$

	static final String KEY_RESTORE_PATH = "restore_path"; //$NON-NLS-1$

	private static final String DEFAULT_EXPORT_FOLDER = "/sdcard/backups/"; //$NON-NLS-1$

	private static final String SYS_APP = "system/"; //$NON-NLS-1$

	private static final String USER_APP = "user/"; //$NON-NLS-1$

	private static Method mdGetPackageSizeInfo;

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

	private ListView lstApps;

	private ProgressDialog progress;

	private Drawable defaultIcon;

	private String versionPrefix;

	private AppCache appCache;

	private DateFormat dateFormatter = DateFormat.getDateTimeInstance( );

	private Handler handler = new Handler( ) {

		@Override
		public void handleMessage( Message msg )
		{
			ArrayAdapter<AppInfoHolder> adapter;

			switch ( msg.what )
			{
				case MSG_INIT_OK :

					adapter = (ArrayAdapter<AppInfoHolder>) lstApps.getAdapter( );

					adapter.setNotifyOnChange( false );

					adapter.clear( );

					for ( AppInfoHolder info : appCache.appList )
					{
						adapter.add( info );
					}

					// should always no selection at this stage
					hideButtons( );

					adapter.notifyDataSetChanged( );

					sendEmptyMessage( MSG_DISMISS_PROGRESS );

					if ( lstApps.getCount( ) == 0 )
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

					final List<ApplicationInfo> apps = (List<ApplicationInfo>) msg.obj;

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

					( (NotificationManager) getSystemService( NOTIFICATION_SERVICE ) ).notify( MSG_COPING_FINISHED,
							nc );

					toggleAllSelection( false );

					if ( Util.getBooleanOption( ApplicationManager.this,
							PREF_KEY_SHOW_BACKUP_STATE ) )
					{
						// reload backup state
						new Thread( new Runnable( ) {

							public void run( )
							{
								reloadBackupState( getPackageManager( ), apps );
							}
						} ).start( );
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

					adapter = (ArrayAdapter<AppInfoHolder>) lstApps.getAdapter( );

					if ( msg.arg1 == 1 )
					{
						adapter.setNotifyOnChange( false );

						adapter.clear( );

						for ( AppInfoHolder info : appCache.appList )
						{
							adapter.add( info );
						}
					}

					adapter.notifyDataSetChanged( );
					break;
				case MSG_REFRESH_PKG_ICON :

					( (ArrayAdapter<AppInfoHolder>) lstApps.getAdapter( ) ).notifyDataSetChanged( );
					break;
			}
		}
	};

	private OnCheckedChangeListener checkListener = new OnCheckedChangeListener( ) {

		public void onCheckedChanged( CompoundButton buttonView,
				boolean isChecked )
		{
			( (AppInfoHolder) lstApps.getItemAtPosition( (Integer) buttonView.getTag( ) ) ).checked = isChecked;

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
			else if ( getSelectedCount( ) == 0 )
			{
				hideButtons( );
			}
		}
	};

	@Override
	protected void onCreate( Bundle savedInstanceState )
	{
		super.onCreate( savedInstanceState );

		defaultIcon = getResources( ).getDrawable( R.drawable.icon );

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

		lstApps = getListView( );

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
					img_type.setImageDrawable( defaultIcon );
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

		lstApps.setAdapter( adapter );
	}

	@Override
	protected void onDestroy( )
	{
		appCache.appList.clear( );
		appCache.appLookup.clear( );

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
		( (NotificationManager) getSystemService( NOTIFICATION_SERVICE ) ).cancel( MSG_COPING_FINISHED );

		super.onStop( );
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

				for ( Iterator<ApplicationInfo> itr = filteredApps.iterator( ); itr.hasNext( ); )
				{
					ApplicationInfo info = itr.next( );

					AppInfoHolder holder = new AppInfoHolder( );
					holder.appInfo = info;

					try
					{
						PackageInfo pi = pm.getPackageInfo( info.packageName, 0 );

						holder.version = versionPrefix + " " //$NON-NLS-1$
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

				appCache.update( dataList );

				appCache.reOrder( Util.getIntOption( ApplicationManager.this,
						PREF_KEY_SORT_ORDER_TYPE,
						ORDER_TYPE_NAME ),
						Util.getIntOption( ApplicationManager.this,
								PREF_KEY_SORT_DIRECTION,
								ORDER_ASC ) );

				handler.sendEmptyMessage( MSG_INIT_OK );

				new Thread( new Runnable( ) {

					public void run( )
					{
						int totalSize = filteredApps.size( );
						int secSize = 16;

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

							PkgSizeObserver observer = new PkgSizeObserver( count );

							for ( int i = 0; i < secCount; i++ )
							{
								observer.invokeGetPkgSize( filteredApps.get( k
										* secSize
										+ i ).packageName, pm );
							}

							try
							{
								count.await( );

								if ( k == num - 1 )
								{
									int type = Util.getIntOption( ApplicationManager.this,
											PREF_KEY_SORT_ORDER_TYPE,
											ORDER_TYPE_NAME );

									if ( type == ORDER_TYPE_CODE_SIZE
											|| type == ORDER_TYPE_DATA_SIZE
											|| type == ORDER_TYPE_CACHE_SIZE
											|| type == ORDER_TYPE_TOTAL_SIZE )
									{
										appCache.reOrder( type,
												Util.getIntOption( ApplicationManager.this,
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
				} ).start( );

				new Thread( new Runnable( ) {

					public void run( )
					{
						ApplicationInfo ai;
						AppInfoHolder holder;

						for ( int i = 0; i < filteredApps.size( ); i++ )
						{
							ai = filteredApps.get( i );

							CharSequence label = ai.loadLabel( pm );

							holder = appCache.appLookup.get( ai.packageName );

							if ( holder != null )
							{
								holder.label = label;
							}
						}

						// reorder by new names
						if ( Util.getIntOption( ApplicationManager.this,
								PREF_KEY_SORT_ORDER_TYPE,
								ORDER_TYPE_NAME ) == ORDER_TYPE_NAME )
						{
							appCache.reOrder( ORDER_TYPE_NAME,
									Util.getIntOption( ApplicationManager.this,
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

						for ( int i = 0; i < filteredApps.size( ); i++ )
						{
							ai = filteredApps.get( i );

							Drawable icon = ai.loadIcon( pm );

							holder = appCache.appLookup.get( ai.packageName );

							if ( holder != null )
							{
								holder.icon = icon;
							}
						}

						handler.sendEmptyMessage( MSG_REFRESH_PKG_ICON );
					}
				} ).start( );

				if ( Util.getBooleanOption( ApplicationManager.this,
						PREF_KEY_SHOW_BACKUP_STATE ) )
				{
					new Thread( new Runnable( ) {

						public void run( )
						{
							reloadBackupState( pm, filteredApps );
						}
					} ).start( );
				}
			}
		} ).start( );
	}

	private void reloadBackupState( final PackageManager pm,
			final List<ApplicationInfo> apps )
	{
		if ( apps == null || apps.size( ) == 0 )
		{
			return;
		}

		String exportFolder = Util.getStringOption( ApplicationManager.this,
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

		for ( int i = 0; i < apps.size( ); i++ )
		{
			ai = apps.get( i );

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
		if ( Util.getIntOption( ApplicationManager.this,
				PREF_KEY_SORT_ORDER_TYPE,
				ORDER_TYPE_NAME ) == ORDER_TYPE_BACKUP_STATE )
		{
			appCache.reOrder( ORDER_TYPE_BACKUP_STATE,
					Util.getIntOption( ApplicationManager.this,
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

	private List<ApplicationInfo> filterApps( List<ApplicationInfo> apps )
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

			for ( ApplicationInfo ai : apps )
			{
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

			for ( ApplicationInfo ai : apps )
			{
				if ( ( ai.flags & ApplicationInfo.FLAG_SYSTEM ) == 0 )
				{
					userApps.add( ai );
				}
			}

			return userApps;
		}

		return apps;
	}

	private boolean ensureSDCard( )
	{
		String state = Environment.getExternalStorageState( );

		return Environment.MEDIA_MOUNTED.equals( state );
	}

	private List<ApplicationInfo> getSelected( )
	{
		int count = lstApps.getCount( );

		ArrayList<ApplicationInfo> apps = new ArrayList<ApplicationInfo>( );

		for ( int i = 0; i < count; i++ )
		{
			AppInfoHolder holder = (AppInfoHolder) lstApps.getItemAtPosition( i );

			if ( holder.checked )
			{
				apps.add( holder.appInfo );
			}
		}

		return apps;
	}

	private int getSelectedCount( )
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

	private void export( final List<ApplicationInfo> apps )
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

				for ( int i = 0; i < apps.size( ); i++ )
				{
					ApplicationInfo app = apps.get( i );

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

	private String getFileName( String fullName )
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

	private void copyFile( File src, File dest ) throws IOException
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
				R.id.mi_preference + 2,
				Menu.NONE,
				R.string.uninstall );
		mi.setIcon( android.R.drawable.ic_menu_delete );

		mi = menu.add( Menu.NONE,
				R.id.mi_preference + 1,
				Menu.NONE,
				R.string.restore );
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
			it.putExtra( PREF_KEY_SORT_DIRECTION, Util.getIntOption( this,
					PREF_KEY_SORT_DIRECTION,
					ORDER_ASC ) );
			it.putExtra( PREF_KEY_SHOW_SIZE, Util.getBooleanOption( this,
					PREF_KEY_SHOW_SIZE ) );
			it.putExtra( PREF_KEY_SHOW_DATE, Util.getBooleanOption( this,
					PREF_KEY_SHOW_DATE ) );
			it.putExtra( PREF_KEY_SHOW_BACKUP_STATE,
					Util.getBooleanOption( this, PREF_KEY_SHOW_BACKUP_STATE ) );

			startActivityForResult( it, REQUEST_SETTINGS );

			return true;
		}
		else if ( item.getItemId( ) == R.id.mi_preference + 1 )
		{
			Intent it = new Intent( this, RestoreAppActivity.class );

			it.putExtra( KEY_RESTORE_PATH,
					new File( Util.getStringOption( this,
							PREF_KEY_APP_EXPORT_DIR,
							DEFAULT_EXPORT_FOLDER ), USER_APP ).getAbsolutePath( ) );

			startActivityForResult( it, REQUEST_RESTORE );

			return true;
		}
		else if ( item.getItemId( ) == R.id.mi_preference + 2 )
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
	}

	@Override
	public boolean onContextItemSelected( MenuItem item )
	{
		int pos = ( (AdapterContextMenuInfo) item.getMenuInfo( ) ).position;

		if ( pos >= 0 && pos < lstApps.getCount( ) )
		{
			final AppInfoHolder ai = (AppInfoHolder) lstApps.getItemAtPosition( pos );

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

						for ( ResolveInfo ri : acts )
						{
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
		}

		return false;
	}

	private void doUninstall( )
	{
		final List<ApplicationInfo> sels = getSelected( );

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

					for ( int i = 0; i < sels.size( ); i++ )
					{
						ApplicationInfo app = sels.get( i );

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

	private void doExport( )
	{
		final List<ApplicationInfo> sels = getSelected( );

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

	private void toggleAllSelection( boolean selected )
	{
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

	private void hideButtons( )
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
	private final class PkgSizeObserver extends IPackageStatsObserver.Stub
	{

		private CountDownLatch count;

		private PkgSizeObserver( CountDownLatch count )
		{
			this.count = count;
		}

		private void invokeGetPkgSize( String pkgName, PackageManager pm )
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
				holder.size = Formatter.formatFileSize( ApplicationManager.this,
						pStats.codeSize )
						+ " + " //$NON-NLS-1$
						+ Formatter.formatFileSize( ApplicationManager.this,
								pStats.dataSize )
						+ " (" //$NON-NLS-1$
						+ Formatter.formatFileSize( ApplicationManager.this,
								pStats.cacheSize )
						+ ')';

				holder.codeSize = pStats.codeSize;
				holder.dataSize = pStats.dataSize;
				holder.cacheSize = pStats.cacheSize;
			}

			count.countDown( );
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

		ArrayList<AppInfoHolder> appList = new ArrayList<AppInfoHolder>( );

		HashMap<String, AppInfoHolder> appLookup = new HashMap<String, AppInfoHolder>( );

		synchronized void update( ArrayList<AppInfoHolder> apps )
		{
			appList.retainAll( apps );

			for ( AppInfoHolder ai : apps )
			{
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

		private void refreshBooleanOption( String key )
		{
			boolean val = getIntent( ).getBooleanExtra( key, true );

			( (CheckBoxPreference) findPreference( key ) ).setChecked( val );
		}

		private void refreshBackupFolder( )
		{
			findPreference( "export_dir" ).setSummary( getIntent( ).getStringExtra( PREF_KEY_APP_EXPORT_DIR ) ); //$NON-NLS-1$
		}

		private void refreshAppType( )
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

		private void refreshSortType( )
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

		private void refreshSortDirection( )
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
