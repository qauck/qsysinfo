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

import java.io.File;
import java.io.FileFilter;
import java.text.Collator;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;

import android.app.AlertDialog;
import android.app.ListActivity;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.DialogInterface.OnClickListener;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.pm.PackageManager.NameNotFoundException;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.preference.CheckBoxPreference;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceCategory;
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
 * RestoreAppActivity
 */
public final class RestoreAppActivity extends ListActivity
{

	private static final int MSG_INIT_OK = 9;
	private static final int MSG_DISMISS_PROGRESS = 10;
	private static final int MSG_SCAN = 11;
	private static final int MSG_PRE_SCAN = 12;

	private static final String PREF_KEY_DEFAULT_RESTORE_DIR = "default_restore_dir"; //$NON-NLS-1$
	private static final String PREF_KEY_APP_RESTORE_DIR = "app_restore_dir"; //$NON-NLS-1$
	private static final String PREF_KEY_SEARCH_SUB_DIR = "search_sub_dir"; //$NON-NLS-1$
	private static final String PREF_KEY_SORT_ORDER_TYPE = "sort_order_type"; //$NON-NLS-1$
	private static final String PREF_KEY_SORT_DIRECTION = "sort_direction"; //$NON-NLS-1$
	private static final String PREF_KEY_SHOW_SIZE = "show_size"; //$NON-NLS-1$
	private static final String PREF_KEY_SHOW_DATE = "show_date"; //$NON-NLS-1$
	private static final String PREF_KEY_SHOW_PATH = "show_path"; //$NON-NLS-1$

	private static final int ORDER_TYPE_NAME = 0;
	private static final int ORDER_TYPE_SIZE = 1;
	private static final int ORDER_TYPE_INSTALL = 2;
	private static final int ORDER_TYPE_DATE = 3;
	private static final int ORDER_TYPE_PATH = 4;

	private static final int ORDER_ASC = 1;
	private static final int ORDER_DESC = -1;

	private ListView lstApps;
	private ProgressDialog progress;

	private String versionPrefix;
	private Drawable defaultIcon;

	private boolean skipUpdate;

	private DateFormat dateFormatter = DateFormat.getDateTimeInstance( );

	private Handler handler = new Handler( ) {

		@Override
		public void handleMessage( Message msg )
		{
			ArrayAdapter<ApkInfo> adapter;

			switch ( msg.what )
			{
				case MSG_INIT_OK :

					adapter = (ArrayAdapter<ApkInfo>) lstApps.getAdapter( );

					adapter.setNotifyOnChange( false );

					adapter.clear( );

					ArrayList<ApkInfo> dataList = (ArrayList<ApkInfo>) msg.obj;

					if ( dataList != null )
					{
						for ( ApkInfo info : dataList )
						{
							adapter.add( info );
						}
					}

					// should always no selection at this stage
					hideButtons( );

					adapter.notifyDataSetChanged( );

					sendEmptyMessage( MSG_DISMISS_PROGRESS );

					if ( lstApps.getCount( ) == 0 )
					{
						Util.shortToast( RestoreAppActivity.this,
								R.string.no_apk_show );
					}

					break;
				case MSG_DISMISS_PROGRESS :

					if ( progress != null )
					{
						progress.dismiss( );
						progress = null;
					}
					break;
				case MSG_SCAN :

					if ( progress != null )
					{
						progress.setMessage( getString( R.string.scanning,
								msg.obj ) );
						progress.setProgress( progress.getProgress( ) + 1 );
					}
					break;
				case MSG_PRE_SCAN :

					if ( progress != null )
					{
						progress.dismiss( );
					}

					progress = new ProgressDialog( RestoreAppActivity.this );
					progress.setMessage( getResources( ).getText( R.string.loading ) );
					progress.setIndeterminate( false );
					progress.setProgressStyle( ProgressDialog.STYLE_HORIZONTAL );
					progress.setMax( msg.arg1 );
					progress.show( );
					break;
			}
		}
	};

	private OnCheckedChangeListener checkListener = new OnCheckedChangeListener( ) {

		public void onCheckedChanged( CompoundButton buttonView,
				boolean isChecked )
		{
			( (ApkInfo) lstApps.getItemAtPosition( (Integer) buttonView.getTag( ) ) ).checked = isChecked;

			View v = findViewById( R.id.app_footer );

			if ( isChecked )
			{
				if ( v.getVisibility( ) != View.VISIBLE )
				{
					v.setVisibility( View.VISIBLE );

					v.startAnimation( AnimationUtils.loadAnimation( RestoreAppActivity.this,
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

		setContentView( R.layout.app_view );

		Button btnAction = ( (Button) findViewById( R.id.btn_export ) );
		btnAction.setText( R.string.restore );
		btnAction.setOnClickListener( new View.OnClickListener( ) {

			public void onClick( View v )
			{
				doRestore( );
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
				CheckBox ckb_app = (CheckBox) view.findViewById( R.id.ckb_app );

				ckb_app.setChecked( !ckb_app.isChecked( ) );
			}
		} );

		ArrayAdapter<ApkInfo> adapter = new ArrayAdapter<ApkInfo>( this,
				R.layout.app_item ) {

			public android.view.View getView( int position,
					android.view.View convertView, android.view.ViewGroup parent )
			{
				View view;
				TextView txt_name, txt_size, txt_ver, txt_time, txt_path;
				ImageView img_type;
				CheckBox ckb_app;

				if ( convertView == null )
				{
					view = RestoreAppActivity.this.getLayoutInflater( )
							.inflate( R.layout.app_item, parent, false );
				}
				else
				{
					view = convertView;
				}

				ApkInfo itm = getItem( position );

				txt_name = (TextView) view.findViewById( R.id.app_name );
				if ( itm.label != null )
				{
					txt_name.setText( itm.label );
				}
				else
				{
					txt_name.setText( itm.file.getName( ) );
				}

				switch ( itm.installed )
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
				if ( Util.getBooleanOption( RestoreAppActivity.this,
						PREF_KEY_SHOW_SIZE ) )
				{
					txt_size.setVisibility( View.VISIBLE );

					if ( itm.sizeString != null )
					{
						txt_size.setText( itm.sizeString );
					}
					else
					{
						txt_size.setText( R.string.unknown );
					}
				}
				else
				{
					txt_size.setVisibility( View.GONE );
				}

				txt_path = (TextView) view.findViewById( R.id.app_path );
				if ( Util.getBooleanOption( RestoreAppActivity.this,
						PREF_KEY_SHOW_PATH ) )
				{
					txt_path.setVisibility( View.VISIBLE );

					txt_path.setText( itm.file.getAbsolutePath( ) );
				}
				else
				{
					txt_path.setVisibility( View.GONE );
				}

				txt_time = (TextView) view.findViewById( R.id.app_time );
				if ( Util.getBooleanOption( RestoreAppActivity.this,
						PREF_KEY_SHOW_DATE ) )
				{
					txt_time.setVisibility( View.VISIBLE );

					txt_time.setText( dateFormatter.format( new Date( itm.file.lastModified( ) ) ) );
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

				return view;
			}
		};

		lstApps.setAdapter( adapter );
	}

	@Override
	protected void onStart( )
	{
		super.onStart( );

		if ( skipUpdate )
		{
			skipUpdate = false;
		}
		else
		{
			loadApps( );
		}
	}

	@Override
	protected void onActivityResult( int requestCode, int resultCode,
			Intent data )
	{
		if ( requestCode == 1 )
		{
			skipUpdate = true;

			if ( Util.updateStringOption( data, this, PREF_KEY_APP_RESTORE_DIR ) )
			{
				skipUpdate = false;
			}

			if ( Util.updateBooleanOption( data, this, PREF_KEY_SEARCH_SUB_DIR ) )
			{
				skipUpdate = false;
			}

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
			Util.updateBooleanOption( data, this, PREF_KEY_SHOW_PATH );

			if ( skipUpdate )
			{
				Comparator<ApkInfo> comp = getComparator( Util.getIntOption( this,
						PREF_KEY_SORT_ORDER_TYPE,
						ORDER_TYPE_NAME ),
						Util.getIntOption( this,
								PREF_KEY_SORT_DIRECTION,
								ORDER_ASC ) );

				if ( comp != null )
				{
					( (ArrayAdapter<ApkInfo>) lstApps.getAdapter( ) ).sort( comp );
				}
			}
		}
	}

	@Override
	public boolean onCreateOptionsMenu( Menu menu )
	{
		MenuItem mi = menu.add( Menu.NONE,
				R.id.mi_preference + 1,
				Menu.NONE,
				R.string.delete_file );
		mi.setIcon( android.R.drawable.ic_menu_delete );

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
			Intent it = new Intent( this, RestoreAppSettings.class );

			it.putExtra( PREF_KEY_DEFAULT_RESTORE_DIR,
					getIntent( ).getStringExtra( ApplicationManager.KEY_RESTORE_PATH ) );
			it.putExtra( PREF_KEY_APP_RESTORE_DIR, Util.getStringOption( this,
					PREF_KEY_APP_RESTORE_DIR,
					null ) );
			it.putExtra( PREF_KEY_SEARCH_SUB_DIR, Util.getBooleanOption( this,
					PREF_KEY_SEARCH_SUB_DIR ) );
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
			it.putExtra( PREF_KEY_SHOW_PATH, Util.getBooleanOption( this,
					PREF_KEY_SHOW_PATH ) );

			startActivityForResult( it, 1 );

			return true;
		}
		else if ( item.getItemId( ) == R.id.mi_preference + 1 )
		{
			final List<ApkInfo> apks = getSelected( );

			if ( apks.size( ) == 0 )
			{
				Util.shortToast( this, R.string.no_apk_selected );
			}
			else
			{
				OnClickListener listener = new OnClickListener( ) {

					public void onClick( DialogInterface dialog, int which )
					{
						ArrayAdapter adapter = ( (ArrayAdapter) lstApps.getAdapter( ) );
						adapter.setNotifyOnChange( false );

						for ( ApkInfo ai : apks )
						{
							boolean deleted = ai.file.delete( );

							if ( deleted )
							{
								adapter.remove( ai );
							}
							else
							{
								Util.shortToast( RestoreAppActivity.this,
										getString( R.string.delete_file_failed,
												ai.file.getAbsolutePath( ) ) );
							}
						}

						adapter.notifyDataSetChanged( );

						if ( getSelectedCount( ) == 0 )
						{
							hideButtons( );
						}
					}
				};

				StringBuilder sb = new StringBuilder( );
				for ( ApkInfo ai : apks )
				{
					sb.append( ai.file.getName( ) ).append( '\n' );
				}

				new AlertDialog.Builder( this ).setTitle( R.string.warning )
						.setMessage( getString( R.string.delete_file_warn,
								sb.toString( ) ) )
						.setPositiveButton( android.R.string.yes, listener )
						.setNegativeButton( android.R.string.no, null )
						.create( )
						.show( );
			}

			return true;
		}

		return false;
	}

	@Override
	public void onCreateContextMenu( ContextMenu menu, View v,
			ContextMenuInfo menuInfo )
	{
		menu.setHeaderTitle( R.string.actions );
		menu.add( R.string.delete_file );
	}

	@Override
	public boolean onContextItemSelected( MenuItem item )
	{
		int pos = ( (AdapterContextMenuInfo) item.getMenuInfo( ) ).position;

		if ( pos >= 0 && pos < lstApps.getCount( ) )
		{
			final ApkInfo ai = (ApkInfo) lstApps.getItemAtPosition( pos );

			OnClickListener listener = new OnClickListener( ) {

				public void onClick( DialogInterface dialog, int which )
				{
					boolean deleted = ai.file.delete( );

					if ( deleted )
					{
						( (ArrayAdapter) lstApps.getAdapter( ) ).remove( ai );

						if ( getSelectedCount( ) == 0 )
						{
							hideButtons( );
						}
					}
					else
					{
						Util.shortToast( RestoreAppActivity.this,
								getString( R.string.delete_file_failed,
										ai.file.getAbsolutePath( ) ) );
					}
				}
			};

			new AlertDialog.Builder( this ).setTitle( R.string.warning )
					.setMessage( getString( R.string.delete_file_warn,
							ai.file.getName( ) ) )
					.setPositiveButton( android.R.string.yes, listener )
					.setNegativeButton( android.R.string.no, null )
					.create( )
					.show( );

			return true;
		}

		return false;
	}

	private ArrayList<File> getFiles( File parent, final boolean recursive )
	{
		final ArrayList<File> files = new ArrayList<File>( );

		FileFilter filter = new FileFilter( ) {

			public boolean accept( File f )
			{
				if ( f.isFile( )
						&& f.getName( ).toLowerCase( ).endsWith( ".apk" ) ) //$NON-NLS-1$
				{
					files.add( f );
				}
				else if ( recursive && f.isDirectory( ) )
				{
					try
					{
						// try skip links
						if ( f.getAbsolutePath( )
								.equals( f.getCanonicalPath( ) ) )
						{
							f.listFiles( this );
						}
					}
					catch ( Exception e )
					{
						Log.e( RestoreAppActivity.class.getName( ),
								e.getLocalizedMessage( ),
								e );
					}
				}
				return false;
			}
		};

		parent.listFiles( filter );

		return files;
	}

	private void loadApps( )
	{
		String appPath = Util.getStringOption( this,
				PREF_KEY_APP_RESTORE_DIR,
				null );

		if ( appPath == null )
		{
			appPath = getIntent( ).getStringExtra( ApplicationManager.KEY_RESTORE_PATH );
		}

		if ( appPath == null )
		{
			handler.sendEmptyMessage( MSG_INIT_OK );
			return;
		}

		final File appFolder = new File( appPath );

		if ( !appFolder.exists( ) || !appFolder.isDirectory( ) )
		{
			handler.sendEmptyMessage( MSG_INIT_OK );
			return;
		}

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
				ArrayList<File> files = getFiles( appFolder,
						Util.getBooleanOption( RestoreAppActivity.this,
								PREF_KEY_SEARCH_SUB_DIR ) );

				ArrayList<ApkInfo> dataList = new ArrayList<ApkInfo>( );

				if ( files.size( ) > 0 )
				{
					handler.sendMessage( handler.obtainMessage( MSG_PRE_SCAN,
							files.size( ),
							0 ) );

					PackageManager pm = getPackageManager( );

					PackageInfo pi;

					for ( File f : files )
					{
						handler.sendMessage( handler.obtainMessage( MSG_SCAN,
								f.getName( ) ) );

						pi = pm.getPackageArchiveInfo( f.getAbsolutePath( ), 0 );

						if ( pi != null )
						{
							ApkInfo holder = new ApkInfo( );

							holder.file = f;
							holder.size = f.length( );
							holder.sizeString = Formatter.formatFileSize( RestoreAppActivity.this,
									holder.size );
							holder.version = pi.versionName == null ? String.valueOf( pi.versionCode )
									: pi.versionName;

							if ( pi.packageName != null )
							{
								try
								{
									PackageInfo ipi = pm.getPackageInfo( pi.packageName,
											0 );

									holder.version = getString( R.string.installed_ver,
											pi.versionName == null ? String.valueOf( pi.versionCode )
													: pi.versionName,
											ipi.versionName == null ? String.valueOf( ipi.versionCode )
													: ipi.versionName );

									if ( ipi.versionCode < pi.versionCode )
									{
										holder.installed = 1;
									}
									else if ( ipi.versionCode == pi.versionCode )
									{
										holder.installed = 2;
									}
									else
									{
										holder.installed = 3;
									}
								}
								catch ( NameNotFoundException e )
								{
									// ignore
								}
							}

							if ( pi.applicationInfo != null )
							{
								holder.label = pm.getApplicationLabel( pi.applicationInfo );
								holder.icon = pm.getApplicationIcon( pi.applicationInfo );
							}

							dataList.add( holder );
						}
					}

					Comparator<ApkInfo> comp = getComparator( Util.getIntOption( RestoreAppActivity.this,
							PREF_KEY_SORT_ORDER_TYPE,
							ORDER_TYPE_NAME ),
							Util.getIntOption( RestoreAppActivity.this,
									PREF_KEY_SORT_DIRECTION,
									ORDER_ASC ) );

					if ( comp != null )
					{
						Collections.sort( dataList, comp );
					}
				}

				handler.sendMessage( handler.obtainMessage( MSG_INIT_OK,
						dataList ) );
			}
		} ).start( );
	}

	private void hideButtons( )
	{
		View v = findViewById( R.id.app_footer );

		if ( v.getVisibility( ) != View.GONE )
		{
			v.setVisibility( View.GONE );

			v.startAnimation( AnimationUtils.loadAnimation( RestoreAppActivity.this,
					R.anim.footer_disappear ) );
		}
	}

	private List<ApkInfo> getSelected( )
	{
		int count = lstApps.getCount( );

		ArrayList<ApkInfo> apps = new ArrayList<ApkInfo>( );

		for ( int i = 0; i < count; i++ )
		{
			ApkInfo holder = (ApkInfo) lstApps.getItemAtPosition( i );

			if ( holder.checked )
			{
				apps.add( holder );
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
			ApkInfo holder = (ApkInfo) lstApps.getItemAtPosition( i );

			if ( holder.checked )
			{
				s++;
			}
		}

		return s;
	}

	private void doRestore( )
	{
		List<ApkInfo> apps = getSelected( );

		if ( apps == null || apps.size( ) == 0 )
		{
			Util.shortToast( this, R.string.no_apk_selected );
		}
		else
		{
			boolean canInstall = false;

			for ( int i = 0; i < apps.size( ); i++ )
			{
				ApkInfo app = apps.get( i );

				Intent it = new Intent( Intent.ACTION_VIEW );

				it.setDataAndType( Uri.fromFile( app.file ),
						"application/vnd.android.package-archive" ); //$NON-NLS-1$

				if ( !canInstall )
				{
					List<ResolveInfo> acts = getPackageManager( ).queryIntentActivities( it,
							0 );

					canInstall = acts.size( ) > 0;
				}

				if ( canInstall )
				{
					startActivity( it );
				}
			}

			if ( !canInstall )
			{
				Util.shortToast( this, R.string.install_fail );

				Log.d( RestoreAppActivity.class.getName( ),
						"No activity found to handle the install request." ); //$NON-NLS-1$
			}
		}
	}

	private void toggleAllSelection( boolean selected )
	{
		int totalCount = lstApps.getCount( );
		for ( int i = 0; i < totalCount; i++ )
		{
			ApkInfo holder = (ApkInfo) lstApps.getItemAtPosition( i );

			holder.checked = selected;
		}

		if ( !selected )
		{
			hideButtons( );
		}

		( (ArrayAdapter) lstApps.getAdapter( ) ).notifyDataSetChanged( );
	}

	private Comparator<ApkInfo> getComparator( int type, final int direction )
	{
		switch ( type )
		{
			case ORDER_TYPE_NAME :
				return new Comparator<ApkInfo>( ) {

					Collator clt = Collator.getInstance( );

					public int compare( ApkInfo obj1, ApkInfo obj2 )
					{
						String lb1 = obj1.label == null ? obj1.file.getName( )
								: obj1.label.toString( );
						String lb2 = obj2.label == null ? obj2.file.getName( )
								: obj2.label.toString( );

						return clt.compare( lb1, lb2 ) * direction;
					}
				};
			case ORDER_TYPE_SIZE :
				return new Comparator<ApkInfo>( ) {

					public int compare( ApkInfo obj1, ApkInfo obj2 )
					{
						return ( obj1.size == obj2.size ? 0
								: ( obj1.size < obj2.size ? -1 : 1 ) )
								* direction;
					}
				};
			case ORDER_TYPE_INSTALL :
				return new Comparator<ApkInfo>( ) {

					public int compare( ApkInfo obj1, ApkInfo obj2 )
					{
						return ( obj1.installed - obj2.installed ) * direction;
					}
				};
			case ORDER_TYPE_DATE :
				return new Comparator<ApkInfo>( ) {

					public int compare( ApkInfo obj1, ApkInfo obj2 )
					{
						long d1 = obj1.file.lastModified( );
						long d2 = obj2.file.lastModified( );

						return ( d1 == d2 ? 0 : ( d1 < d2 ? -1 : 1 ) )
								* direction;
					}
				};
			case ORDER_TYPE_PATH :
				return new Comparator<ApkInfo>( ) {

					public int compare( ApkInfo obj1, ApkInfo obj2 )
					{
						return obj1.file.compareTo( obj2.file ) * direction;
					}
				};
		}

		return null;
	}

	/**
	 * ApkInfo
	 */
	private static final class ApkInfo
	{

		File file;
		CharSequence label;
		String version;
		String sizeString;
		long size;
		Drawable icon;
		int installed;
		boolean checked;
	}

	/**
	 * RestoreAppSettings
	 */
	public static final class RestoreAppSettings extends PreferenceActivity
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

			Preference perfRestoreFolder = new Preference( this );
			perfRestoreFolder.setKey( PREF_KEY_APP_RESTORE_DIR );
			perfRestoreFolder.setTitle( R.string.scan_dir );
			pc.addPreference( perfRestoreFolder );

			CheckBoxPreference perfSubDir = new CheckBoxPreference( this );
			perfSubDir.setKey( PREF_KEY_SEARCH_SUB_DIR );
			perfSubDir.setTitle( R.string.search_subdir );
			perfSubDir.setSummary( R.string.search_subdir_sum );
			pc.addPreference( perfSubDir );

			CheckBoxPreference perfShowSize = new CheckBoxPreference( this );
			perfShowSize.setKey( PREF_KEY_SHOW_SIZE );
			perfShowSize.setTitle( R.string.show_file_size );
			perfShowSize.setSummary( R.string.show_file_size_sum );
			pc.addPreference( perfShowSize );

			CheckBoxPreference perfShowDate = new CheckBoxPreference( this );
			perfShowDate.setKey( PREF_KEY_SHOW_DATE );
			perfShowDate.setTitle( R.string.show_file_date );
			perfShowDate.setSummary( R.string.show_file_date_sum );
			pc.addPreference( perfShowDate );

			CheckBoxPreference perfShowPath = new CheckBoxPreference( this );
			perfShowPath.setKey( PREF_KEY_SHOW_PATH );
			perfShowPath.setTitle( R.string.show_file_path );
			perfShowPath.setSummary( R.string.show_file_path_sum );
			pc.addPreference( perfShowPath );

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

			refreshRestoreFolder( );
			refreshBooleanOption( PREF_KEY_SEARCH_SUB_DIR );
			refreshSortType( );
			refreshSortDirection( );
			refreshBooleanOption( PREF_KEY_SHOW_SIZE );
			refreshBooleanOption( PREF_KEY_SHOW_DATE );
			refreshBooleanOption( PREF_KEY_SHOW_PATH );

			setResult( RESULT_OK, getIntent( ) );
		}

		private void refreshRestoreFolder( )
		{
			String path = getIntent( ).getStringExtra( PREF_KEY_APP_RESTORE_DIR );
			if ( path == null )
			{
				path = getIntent( ).getStringExtra( PREF_KEY_DEFAULT_RESTORE_DIR );
			}

			findPreference( PREF_KEY_APP_RESTORE_DIR ).setSummary( path );
		}

		private void refreshBooleanOption( String key )
		{
			boolean val = getIntent( ).getBooleanExtra( key, true );

			( (CheckBoxPreference) findPreference( key ) ).setChecked( val );
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
				case ORDER_TYPE_SIZE :
					label = getString( R.string.file_size );
					break;
				case ORDER_TYPE_INSTALL :
					label = getString( R.string.installation );
					break;
				case ORDER_TYPE_DATE :
					label = getString( R.string.file_date );
					break;
				case ORDER_TYPE_PATH :
					label = getString( R.string.file_path );
					break;
			}

			findPreference( PREF_KEY_SORT_ORDER_TYPE ).setSummary( label );
		}

		private void refreshSortDirection( )
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

			if ( PREF_KEY_APP_RESTORE_DIR.equals( preference.getKey( ) ) )
			{
				final EditText txt = new EditText( this );
				final String defaultPath = it.getStringExtra( PREF_KEY_DEFAULT_RESTORE_DIR );

				String path = it.getStringExtra( PREF_KEY_APP_RESTORE_DIR );
				if ( path == null )
				{
					path = defaultPath;
				}
				txt.setText( path );

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

						it.putExtra( PREF_KEY_APP_RESTORE_DIR, path );

						dialog.dismiss( );

						refreshRestoreFolder( );
					}
				};

				new AlertDialog.Builder( this ).setTitle( R.string.scan_dir )
						.setPositiveButton( android.R.string.ok, listener )
						.setNegativeButton( android.R.string.cancel, null )
						.setView( txt )
						.create( )
						.show( );

				return true;
			}
			else if ( PREF_KEY_SEARCH_SUB_DIR.equals( preference.getKey( ) ) )
			{
				it.putExtra( PREF_KEY_SEARCH_SUB_DIR,
						( (CheckBoxPreference) findPreference( PREF_KEY_SEARCH_SUB_DIR ) ).isChecked( ) );

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
			else if ( PREF_KEY_SHOW_PATH.equals( preference.getKey( ) ) )
			{
				it.putExtra( PREF_KEY_SHOW_PATH,
						( (CheckBoxPreference) findPreference( PREF_KEY_SHOW_PATH ) ).isChecked( ) );

				return true;
			}
			else if ( PREF_KEY_SORT_ORDER_TYPE.equals( preference.getKey( ) ) )
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
								getString( R.string.file_size ),
								getString( R.string.installation ),
								getString( R.string.file_date ),
								getString( R.string.file_path ),
						},
								it.getIntExtra( PREF_KEY_SORT_ORDER_TYPE,
										ORDER_TYPE_NAME ),
								listener )
						.create( )
						.show( );

				return true;
			}
			else if ( PREF_KEY_SORT_DIRECTION.equals( preference.getKey( ) ) )
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
