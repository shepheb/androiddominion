package dominion.android;

import java.util.ArrayList;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import android.util.TypedValue;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup.LayoutParams;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import dominion.Decision;
import dominion.Exchange;
import dominion.Option;
import dominion.Player;
import dominion.android.GameService.GameBinder;

public class DominionA extends Activity {
	private Exchange exchange;
	boolean serviceBound = false;
	private Player lastPlayer = null;
	
	protected TextView newPlayer, gameOverWinner;
	protected LinearLayout logLayout, infoLayout, optionsLayout, decisionLayout, gameOverLayout, gameOverPlayers;
	
	protected ScrollView decisionScroller;
	
	protected int lastClick = -1;
	protected TextView lastClickTarget; 

	private OnClickListener optionListener = new OnClickListener() {
		public void onClick(View v) {
			TextView tv = (TextView) v;
			int index = ((Integer) tv.getTag()).intValue();
			
			if(index != lastClick) {
				if(lastClickTarget != null) {
					lastClickTarget.setBackgroundColor(0xff000000);
				}
				tv.setBackgroundColor(0xff444444);
				lastClickTarget = tv;
				lastClick = index;
			} else {
				// actually send the response.
				lastClick = -1; // reset to prevent one-click decisions.
				Exchange ex = DominionA.this.exchange;
				Decision decision = (Decision) ex.decision;
				Option opt = (Option) decision.options().get(index);
				ex.postResponse(opt.key());
				DominionA.this.handleDecision();
			}
		}
	};
	
	private ServiceConnection mConnection = new ServiceConnection() {
		public void onServiceConnected(ComponentName className, IBinder service) {
			// This is called when the connection with the service has been
			// established, giving us the object we can use to
			// interact with the service.
			Log.i(Constants.TAG, "Bound. service = " + service);
			exchange = ((GameBinder) service).exchange;
			Constants.exchange = exchange;
			serviceBound = true;
			DominionA.this.handleDecision();
		}

		public void onServiceDisconnected(ComponentName className) {
			// This is called when the connection with the service has been
			// unexpectedly disconnected -- that is, its process crashed.
			Toast.makeText(getApplicationContext(), "Fatal error: Service connection lost!", Toast.LENGTH_SHORT).show();
		}
	};

	protected void handleDecision() {
		Log.i(Constants.TAG, "Calling waitForDecision.");
		exchange.waitForDecision();
		Log.i(Constants.TAG, "waitForDecision returned");
		
		Decision decision = (Decision) exchange.decision;

		// display the show-to-player-X if it's not the same player as last time.
		if(exchange.gameOver) {
			showGameOver();
		} else if(decision.player() != lastPlayer) {
			decisionLayout.setVisibility(View.GONE);
			gameOverLayout.setVisibility(View.GONE);
			lastPlayer = decision.player();
			newPlayer.setText("Please give the phone to " + decision.player().name() + ".\nTap here to continue.");
			newPlayer.setVisibility(View.VISIBLE);
			Log.i(Constants.TAG, "Showing new player screen");
		} else {
			showDecision();
		}
	}
	
	
	protected void showDecision() {
		decisionScroller.fullScroll(ScrollView.FOCUS_UP);
		
		Decision decision = (Decision) exchange.decision;
		
		TextView playerName = (TextView) findViewById(R.id.playerName);
		playerName.setText(decision.player().name());
		
		logLayout.removeAllViews();
		ArrayList<String> logs = exchange.getLogs();
		int start = logs.size() - (exchange.getLogSize() - decision.player().lastLogIndex());
		Log.i(Constants.TAG, "logs.size() = " + logs.size() + ", exchange.getLogSize() = " + exchange.getLogSize() + ", lastLogIndex()=" + decision.player().lastLogIndex());
		for(int i = start; i < logs.size(); i++) {
			TextView t = new TextView(this);
			Colorizer.colorize(t, logs.get(i));
			t.setTextSize(TypedValue.COMPLEX_UNIT_PT, 6);
			logLayout.addView(t);
		}
		
		TextView message = (TextView) findViewById(R.id.message);
		Colorizer.colorize(message, decision.message());
		
		infoLayout.removeAllViews();
		for(int i = 0; i < decision.info().size(); i++) {
			TextView t = new TextView(this);
			Colorizer.colorize(t, (String) decision.info().get(i));
			t.setTextSize(TypedValue.COMPLEX_UNIT_PT, 6);
			infoLayout.addView(t);
		}
		
		optionsLayout.removeAllViews();
		for(int i = 0; i < decision.options().size(); i++) {
			Option o = (Option) decision.options().get(i);
			TextView t = new TextView(this);
			Colorizer.colorize(t, o.text());
			t.setTextSize(TypedValue.COMPLEX_UNIT_PT, 8);
			t.setClickable(true);
			t.setTag(new Integer(i));
			t.setOnClickListener(optionListener);
			
			LinearLayout.LayoutParams llp = new LinearLayout.LayoutParams(LayoutParams.FILL_PARENT, LayoutParams.WRAP_CONTENT);
		    llp.setMargins(0, 10, 0, 0); // llp.setMargins(left, top, right, bottom);
		    t.setLayoutParams(llp);
			
			optionsLayout.addView(t);
		}
		
		decisionLayout.setVisibility(View.VISIBLE);
		Log.i(Constants.TAG, "Decision visible");
	}
	
	protected void showGameOver() {
		decisionLayout.setVisibility(View.GONE);
		newPlayer.setVisibility(View.GONE);
		Log.i(Constants.TAG, "Showing game over screen");
		
		gameOverPlayers.removeAllViews();
		
		int winnerScore = -100;
		ArrayList<Player> winners = new ArrayList<Player>();
		Log.i(Constants.TAG, "Looping over players.");
		for(int i = 0; i < Constants.service.game.players().size(); i++) {
			Log.i(Constants.TAG, "Top of loop");
			Player p = (Player) Constants.service.game.players().get(i);
			Log.i(Constants.TAG, "Player: " + p.name());
			TextView tv = new TextView(this);
			int score = p.calculateScore();
			Log.i(Constants.TAG, "Score: " + score);
			if (score > winnerScore) {
				Log.i(Constants.TAG, "New winner");
				winners.clear();
				winners.add(p);
				winnerScore = score;
			} else if (score == winnerScore) {
				winners.add(p);
				Log.i(Constants.TAG, "Adding to tie");
			}
			
			tv.setText(p.name() + ": " + score + " points in " + p.turn() + " turns.");
			gameOverPlayers.addView(tv);
			Log.i(Constants.TAG, "Bottom of loop");
		}
		
		Log.i(Constants.TAG, "Winner count: " + winners.size());
		
		if (winners.size() == 1) {
			gameOverWinner.setText(winners.get(0).name() + " wins!");
		} else {
			ArrayList<Player> realWinners = new ArrayList<Player>();
			int winnerTurns = Integer.MAX_VALUE;
			for(Player p : winners) {
				Log.i(Constants.TAG, "Winner loop: " + p.name());
				if(p.turn() < winnerTurns) {
					Log.i(Constants.TAG, "New realWinner");
					realWinners.clear();
					realWinners.add(p);
					winnerTurns = p.turn();
				} else if(p.turn() == winnerTurns) {
					realWinners.add(p);
					Log.i(Constants.TAG, "Adding to tie");
				}
			}
			
			Log.i(Constants.TAG, "RealWinner count: " + realWinners.size());
			
			if(realWinners.size() == 1) {
				gameOverWinner.setText(realWinners.get(0).name() + " wins!");
			} else {
				StringBuffer sb = new StringBuffer();
				for(int i = 0; i < realWinners.size(); i++) {
					sb.append(realWinners.get(i).name());
					if(i+2 < realWinners.size())
						sb.append(", ");
					else if(i+2 == realWinners.size())
						sb.append(" and ");
				}
				gameOverWinner.setText("Tie between: " + sb.toString());
			}
		}
		Log.i(Constants.TAG, "Bottom of gameOver logic.");
		gameOverLayout.setVisibility(View.VISIBLE);
	}
	
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		Log.i(Constants.TAG, "DominionA.onCreate");
		setContentView(R.layout.dominion);
		newPlayer = (TextView) findViewById(R.id.newPlayer);
		newPlayer.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				Log.i(Constants.TAG, "New player screen clicked, showing decision");
				DominionA.this.newPlayer.setVisibility(View.GONE);
				DominionA.this.showDecision();
			}
		});
		
		decisionScroller = (ScrollView) findViewById(R.id.decisionScroller);
		
		logLayout = (LinearLayout) findViewById(R.id.logLayout);
		infoLayout = (LinearLayout) findViewById(R.id.infoLayout);
		optionsLayout = (LinearLayout) findViewById(R.id.optionsLayout);
		decisionLayout = (LinearLayout) findViewById(R.id.decision);
		
		gameOverLayout = (LinearLayout) findViewById(R.id.gameOver);
		gameOverPlayers = (LinearLayout) findViewById(R.id.gameOverPlayers);
		gameOverWinner = (TextView) findViewById(R.id.gameOverWinner);
	}
	
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
	    MenuInflater inflater = getMenuInflater();
	    inflater.inflate(R.menu.menu, menu);
	    return true;
	}
	
	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch(item.getItemId()) {
		case R.id.miViewLogs:
			startActivity(new Intent(this.getApplicationContext(), LogsA.class));
			return true;
		case R.id.miKingdom:
			startActivity(new Intent(this.getApplicationContext(), KingdomA.class));
			return true;
		default:
			return super.onOptionsItemSelected(item);
		}
	}

	@Override
	protected void onStart() {
		super.onStart();
		// Bind to the service
		Log.i(Constants.TAG, "DominionA.onStart. Calling bindService.");
		bindService(new Intent(this.getApplicationContext(), GameService.class), mConnection,
				Context.BIND_AUTO_CREATE);
	}

	@Override
	protected void onStop() {
		super.onStop();
		// Unbind from the service
		if (serviceBound) {
			unbindService(mConnection);
			serviceBound = false;
		}
	}
}
