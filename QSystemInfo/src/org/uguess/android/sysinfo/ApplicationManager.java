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

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ListActivity;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.ProgressDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.DialogInterface.OnClickListener;
import android.content.SharedPreferences.Editor;
import android.content.pm.ApplicationInfo;
import android.content.pm.IPackageStatsObserver;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageStats;
import android.content.pm.PackageManager.NameNotFoundException;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.os.RemoteException;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceScreen;
import android.text.format.Formatter;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.CompoundButton.OnCheckedChangeListener;

/**
 * ApplicationManager
 */
public final class ApplicationManager extends ListActivity
{

	private static final int MSG_COPING = 1;
	private static final int MSG_COPING_ERROR = 2;
	private static final int MSG_COPING_FINISHED = 3;
	private static final int MSG_INIT_OK = 9;
	private static final int MSG_DISMISS_PROGRESS = 10;
	private static final int MSG_REFRESH_PKG_SIZE = 11;
	private static final int MSG_REFRESH_PKG_LABEL = 12;
	private static final int MSG_REFRESH_PKG_ICON = 13;

	private static final int APP_TYPE_ALL = 0;
	private static final int APP_TYPE_SYS = 1;
	private static final int APP_TYPE_USER = 2;

	private static final int REQUEST_SETTINGS = 1;

	private static final String PREF_KEY_FILTER_APP_TYPE = "filter_app_type"; //$NON-NLS-1$
	private static final String PREF_KEY_APP_EXPORT_DIR = "app_export_dir"; //$NON-NLS-1$

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

	private PackageEventReceiver pkgEventReceiver = new PackageEventReceiver( );

	private ProgressDialog progress;

	private Drawable defaultIcon;

	private String versionPrefix;

	private boolean needReload;

	private Handler handler = new Handler( ) {

		@Override
		public void handleMessage( Message msg )
		{
			AppInfoHolder holder;

			switch ( msg.what )
			{
				case MSG_INIT_OK :

					ArrayAdapter<AppInfoHolder> adapter = (ArrayAdapter<AppInfoHolder>) getListView( ).getAdapter( );

					adapter.setNotifyOnChange( false );

					adapter.clear( );

					ArrayList<AppInfoHolder> data = (ArrayList<AppInfoHolder>) msg.obj;

					if ( data != null )
					{
						for ( AppInfoHolder info : data )
						{
							adapter.add( info );
						}
					}

					adapter.notifyDataSetChanged( );

					sendEmptyMessage( MSG_DISMISS_PROGRESS );

					if ( lstApps.getCount( ) == 0 )
					{
						Toast.makeText( ApplicationManager.this,
								R.string.no_app_show,
								Toast.LENGTH_SHORT ).show( );
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

					if ( progress != null )
					{
						progress.dismiss( );
						progress = null;
					}

					Toast.makeText( ApplicationManager.this,
							getString( R.string.copy_error,
									( (Exception) msg.obj ).getLocalizedMessage( ) ),
							Toast.LENGTH_LONG )
							.show( );
					break;
				case MSG_COPING_FINISHED :

					if ( progress != null )
					{
						progress.setMessage( getString( R.string.exported,
								msg.obj ) );
						progress.setProgress( progress.getMax( ) );
						progress.dismiss( );
						progress = null;
					}

					Toast.makeText( ApplicationManager.this,
							getString( R.string.exported_to,
									msg.obj,
									getAppExportDir( ) ),
							Toast.LENGTH_SHORT ).show( );

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
							getString( R.string.exported, msg.obj ),
							pit );

					( (NotificationManager) getSystemService( NOTIFICATION_SERVICE ) ).notify( MSG_COPING_FINISHED,
							nc );
					break;
				case MSG_DISMISS_PROGRESS :

					if ( progress != null )
					{
						progress.dismiss( );
						progress = null;
					}
					break;
				case MSG_REFRESH_PKG_SIZE :

					// to ignore some outdated requests
					if ( msg.arg1 < lstApps.getCount( ) )
					{
						PackageStats ps = (PackageStats) msg.obj;
						holder = (AppInfoHolder) lstApps.getItemAtPosition( msg.arg1 );
						holder.size = Formatter.formatFileSize( ApplicationManager.this,
								ps.codeSize )
								+ " + " //$NON-NLS-1$
								+ Formatter.formatFileSize( ApplicationManager.this,
										ps.dataSize );

						this.removeCallbacks( refreshTask );
						this.postDelayed( refreshTask, 500 );
					}
					break;
				case MSG_REFRESH_PKG_LABEL :

					ArrayList<CharSequence> labels = (ArrayList<CharSequence>) msg.obj;

					for ( int i = 0; i < labels.size( ); i++ )
					{
						holder = (AppInfoHolder) lstApps.getItemAtPosition( i );

						holder.label = labels.get( i );
					}

					( (ArrayAdapter) lstApps.getAdapter( ) ).notifyDataSetChanged( );
					break;
				case MSG_REFRESH_PKG_ICON :

					ArrayList<Drawable> icons = (ArrayList<Drawable>) msg.obj;

					for ( int i = 0; i < icons.size( ); i++ )
					{
						holder = (AppInfoHolder) lstApps.getItemAtPosition( i );

						holder.icon = icons.get( i );
					}

					( (ArrayAdapter) lstApps.getAdapter( ) ).notifyDataSetChanged( );
					break;
			}
		}
	};

	private Runnable refreshTask = new Runnable( ) {

		public void run( )
		{
			( (ArrayAdapter) lstApps.getAdapter( ) ).notifyDataSetChanged( );
		}
	};

	private OnCheckedChangeListener checkListener = new OnCheckedChangeListener( ) {

		public void onCheckedChanged( CompoundButton buttonView,
				boolean isChecked )
		{
			( (AppInfoHolder) lstApps.getItemAtPosition( (Integer) buttonView.getTag( ) ) ).checked = isChecked;
		}
	};

	@Override
	protected void onCreate( Bundle savedInstanceState )
	{
		super.onCreate( savedInstanceState );

		defaultIcon = getResources( ).getDrawable( R.drawable.icon );

		versionPrefix = getResources( ).getString( R.string.version );

		lstApps = getListView( );

		lstApps.setFastScrollEnabled( true );

		lstApps.setOnItemClickListener( new OnItemClickListener( ) {

			public void onItemClick( AdapterView<?> parent, View view,
					int position, long id )
			{
				AppInfoHolder holder = (AppInfoHolder) parent.getItemAtPosition( position );

				Intent intent = new Intent( Intent.ACTION_VIEW );

				intent.setClassName( "com.android.settings", //$NON-NLS-1$
						"com.android.settings.InstalledAppDetails" ); //$NON-NLS-1$
				intent.putExtra( "com.android.settings.ApplicationPkgName", //$NON-NLS-1$
						holder.appInfo.packageName );

				startActivity( intent );
			}
		} );

		ArrayAdapter<AppInfoHolder> adapter = new ArrayAdapter<AppInfoHolder>( ApplicationManager.this,
				R.layout.app_item ) {

			public android.view.View getView( int position,
					android.view.View convertView, android.view.ViewGroup parent )
			{
				View view;
				TextView txt_name, txt_size, txt_ver;
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

				txt_ver = (TextView) view.findViewById( R.id.app_version );
				if ( itm.version != null )
				{
					txt_ver.setText( versionPrefix + " " + itm.version ); //$NON-NLS-1$
				}
				else
				{
					txt_ver.setText( "" ); //$NON-NLS-1$
				}

				txt_size = (TextView) view.findViewById( R.id.app_size );
				if ( itm.size != null )
				{
					txt_size.setText( itm.size );
				}
				else
				{
					txt_size.setText( R.string.computing );
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

				return view;
			}
		};

		lstApps.setAdapter( adapter );

		pkgEventReceiver.registerReceiver( );

		needReload = true;
	}

	@Override
	protected void onDestroy( )
	{
		( (NotificationManager) getSystemService( NOTIFICATION_SERVICE ) ).cancel( MSG_COPING_FINISHED );

		unregisterReceiver( pkgEventReceiver );

		super.onDestroy( );
	}

	@Override
	protected void onResume( )
	{
		super.onResume( );

		if ( needReload )
		{
			needReload = false;

			loadApps( );
		}
	}

	private String getAppExportDir( )
	{
		SharedPreferences sp = getPreferences( Context.MODE_PRIVATE );

		return sp.getString( PREF_KEY_APP_EXPORT_DIR, DEFAULT_EXPORT_FOLDER );
	}

	private void setAppExportDir( String val )
	{
		SharedPreferences sp = getPreferences( Context.MODE_PRIVATE );

		Editor et = sp.edit( );
		if ( val == null )
		{
			et.remove( PREF_KEY_APP_EXPORT_DIR );
		}
		else
		{
			et.putString( PREF_KEY_APP_EXPORT_DIR, val );
		}
		et.commit( );
	}

	private int getAppFilterType( )
	{
		SharedPreferences sp = getPreferences( Context.MODE_PRIVATE );

		return sp.getInt( PREF_KEY_FILTER_APP_TYPE, APP_TYPE_ALL );
	}

	private void setAppFilterType( int type )
	{
		SharedPreferences sp = getPreferences( Context.MODE_PRIVATE );

		Editor et = sp.edit( );
		et.putInt( PREF_KEY_FILTER_APP_TYPE, type );
		et.commit( );
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

				Collections.sort( filteredApps,
						new ApplicationInfo.DisplayNameComparator( pm ) );

				ArrayList<AppInfoHolder> dataList = new ArrayList<AppInfoHolder>( );

				for ( Iterator<ApplicationInfo> itr = filteredApps.iterator( ); itr.hasNext( ); )
				{
					ApplicationInfo info = itr.next( );

					AppInfoHolder holder = new AppInfoHolder( );
					holder.appInfo = info;

					try
					{
						PackageInfo pi = pm.getPackageInfo( info.packageName, 0 );

						holder.version = pi.versionName;
					}
					catch ( NameNotFoundException e )
					{
						Log.e( ApplicationManager.class.getName( ),
								e.getLocalizedMessage( ),
								e );
					}

					dataList.add( holder );
				}

				handler.sendMessage( handler.obtainMessage( MSG_INIT_OK,
						dataList ) );

				new Thread( new Runnable( ) {

					public void run( )
					{
						for ( int i = 0; i < filteredApps.size( ); i++ )
						{
							invokeGetPkgSize( i,
									filteredApps.get( i ).packageName,
									pm );
						}

					}
				} ).start( );

				new Thread( new Runnable( ) {

					public void run( )
					{
						ArrayList<CharSequence> labels = new ArrayList<CharSequence>( );
						for ( int i = 0; i < filteredApps.size( ); i++ )
						{
							CharSequence label = filteredApps.get( i )
									.loadLabel( pm );

							labels.add( label );
						}

						handler.sendMessage( handler.obtainMessage( MSG_REFRESH_PKG_LABEL,
								labels ) );

						ArrayList<Drawable> icons = new ArrayList<Drawable>( );
						for ( int i = 0; i < filteredApps.size( ); i++ )
						{
							Drawable icon = filteredApps.get( i ).loadIcon( pm );

							icons.add( icon );
						}

						handler.sendMessage( handler.obtainMessage( MSG_REFRESH_PKG_ICON,
								icons ) );
					}
				} ).start( );
			}
		} ).start( );
	}

	private List<ApplicationInfo> filterApps( List<ApplicationInfo> apps )
	{
		if ( apps == null || apps.size( ) == 0 )
		{
			return apps;
		}

		int type = getAppFilterType( );

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

	private void invokeGetPkgSize( int idx, String pkgName, PackageManager pm )
	{
		if ( mdGetPackageSizeInfo != null )
		{
			try
			{
				mdGetPackageSizeInfo.invoke( pm,
						pkgName,
						new PkgSizeObserver( idx ) );
			}
			catch ( Exception e )
			{
				Log.e( ApplicationManager.class.getName( ),
						e.getLocalizedMessage( ),
						e );
			}
		}
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

	private void export( final List<ApplicationInfo> apps )
	{
		if ( apps == null || apps.isEmpty( ) )
		{
			Toast.makeText( this, R.string.no_app_selected, Toast.LENGTH_SHORT )
					.show( );
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
				String exportFolder = getAppExportDir( );

				File output = new File( exportFolder );

				if ( !output.exists( ) )
				{
					if ( !output.mkdirs( ) )
					{
						handler.sendMessage( Message.obtain( handler,
								MSG_COPING_ERROR,
								new IOException( getString( R.string.error_create_folder,
										output.getAbsolutePath( ) ) ) ) );

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
								new IOException( getString( R.string.error_create_folder,
										sysoutput.getAbsolutePath( ) ) ) ) );

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
								new IOException( getString( R.string.error_create_folder,
										useroutput.getAbsolutePath( ) ) ) ) );

						return;
					}
				}

				for ( int i = 0; i < apps.size( ); i++ )
				{
					ApplicationInfo app = apps.get( i );

					String src = app.sourceDir;

					if ( src != null )
					{
						File srcFile = new File( src );

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
								// Thread.sleep( 500 );
							}
							catch ( Exception e )
							{
								Log.e( ApplicationManager.class.getName( ),
										e.getLocalizedMessage( ),
										e );

								handler.sendMessage( Message.obtain( handler,
										MSG_COPING_ERROR,
										e ) );
								return;
							}
						}
					}
				}

				handler.sendMessage( Message.obtain( handler,
						MSG_COPING_FINISHED,
						apps.size( ) ) );
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
			String nDir = data.getStringExtra( PREF_KEY_APP_EXPORT_DIR );

			if ( nDir != null )
			{
				nDir = nDir.trim( );

				if ( nDir.length( ) == 0 )
				{
					nDir = null;
				}
			}

			if ( !getAppExportDir( ).equals( nDir ) )
			{
				setAppExportDir( nDir );
			}

			int nt = data.getIntExtra( PREF_KEY_FILTER_APP_TYPE, APP_TYPE_ALL );

			if ( nt != getAppFilterType( ) )
			{
				setAppFilterType( nt );
				loadApps( );
			}
		}
	}

	@Override
	public boolean onCreateOptionsMenu( Menu menu )
	{
		MenuInflater inflater = getMenuInflater( );
		inflater.inflate( R.menu.apps_options, menu );
		return true;
	}

	@Override
	public boolean onOptionsItemSelected( MenuItem item )
	{
		if ( item.getItemId( ) == R.id.mi_export )
		{
			final List<ApplicationInfo> sels = getSelected( );

			if ( sels == null || sels.size( ) == 0 )
			{
				Toast.makeText( this,
						R.string.no_app_selected,
						Toast.LENGTH_SHORT ).show( );
			}
			else if ( !ensureSDCard( ) )
			{
				Toast.makeText( this, R.string.error_sdcard, Toast.LENGTH_SHORT )
						.show( );
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
								getAppExportDir( ) ) )
						.setPositiveButton( R.string.cont, listener )
						.setNegativeButton( android.R.string.cancel, listener )
						.create( )
						.show( );
			}
			return true;
		}
		else if ( item.getItemId( ) == R.id.mi_select_all )
		{
			toggleAllSelection( true );

			return true;
		}
		else if ( item.getItemId( ) == R.id.mi_deselect_all )
		{
			toggleAllSelection( false );

			return true;
		}
		else if ( item.getItemId( ) == R.id.mi_preference )
		{
			Intent intent = new Intent( Intent.ACTION_VIEW );

			intent.setClass( this, AppSettings.class );

			intent.putExtra( PREF_KEY_FILTER_APP_TYPE, getAppFilterType( ) );
			intent.putExtra( PREF_KEY_APP_EXPORT_DIR, getAppExportDir( ) );

			startActivityForResult( intent, REQUEST_SETTINGS );

			return true;
		}

		return false;
	}

	private void toggleAllSelection( boolean selected )
	{
		// reset hidden item states
		int totalCount = lstApps.getCount( );
		for ( int i = 0; i < totalCount; i++ )
		{
			AppInfoHolder holder = (AppInfoHolder) lstApps.getItemAtPosition( i );

			holder.checked = selected;
		}

		( (ArrayAdapter) lstApps.getAdapter( ) ).notifyDataSetChanged( );
	}

	/**
	 * PackageSizeObserver
	 */
	private final class PkgSizeObserver extends IPackageStatsObserver.Stub
	{

		int idx;

		PkgSizeObserver( int idx )
		{
			this.idx = idx;
		}

		public void onGetStatsCompleted( PackageStats pStats, boolean succeeded )
				throws RemoteException
		{
			Message msg = handler.obtainMessage( MSG_REFRESH_PKG_SIZE );
			msg.obj = pStats;
			msg.arg1 = idx;
			handler.sendMessage( msg );
		}

	}

	/**
	 * PackageEventReceiver
	 */
	private final class PackageEventReceiver extends BroadcastReceiver
	{

		void registerReceiver( )
		{
			IntentFilter filter = new IntentFilter( Intent.ACTION_PACKAGE_ADDED );
			filter.addAction( Intent.ACTION_PACKAGE_REMOVED );
			filter.addAction( Intent.ACTION_PACKAGE_CHANGED );
			filter.addDataScheme( "package" ); //$NON-NLS-1$

			ApplicationManager.this.registerReceiver( this, filter );
		}

		@Override
		public void onReceive( Context context, Intent intent )
		{
			ApplicationManager.this.needReload = true;
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

		boolean checked;
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

			setResult( RESULT_OK, getIntent( ) );
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

			return false;
		}
	}
}