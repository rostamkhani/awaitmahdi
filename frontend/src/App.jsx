import React, { useState, useEffect, useRef } from 'react';
import { v4 as uuidv4 } from 'uuid';
import Cookies from 'js-cookie';
import { heartbeat, register, login } from './api';
import './index.css';
import AnimatedCounter from './AnimatedCounter';
import salavatImage from './assets/salavat_button_1.png';

function App() {
  const [guestUuid, setGuestUuid] = useState('');
  const [user, setUser] = useState(null);
  const [localCount, setLocalCount] = useState(0); // Unsaved clicks
  const [isClicked, setIsClicked] = useState(false); // Track click state for animation
  
  // Initialize stats from cookie if available
  const [stats, setStats] = useState(() => {
    const savedStats = Cookies.get('stats');
    try {
      return savedStats ? JSON.parse(savedStats) : {
        today_total: 0,
        all_time_total: 0,
        user_today: 0,
        user_total: 0
      };
    } catch (e) {
      return {
        today_total: 0,
        all_time_total: 0,
        user_today: 0,
        user_total: 0
      };
    }
  });
  
  const [showAuth, setShowAuth] = useState(false);
  const [authMode, setAuthMode] = useState('login'); // login or register
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');

  const localCountRef = useRef(localCount);

  // Keep ref updated for intervals/cleanup
  useEffect(() => {
    localCountRef.current = localCount;
  }, [localCount]);

  // Initialization
  useEffect(() => {
    // Guest UUID
    let uuid = Cookies.get('guest_uuid');
    if (!uuid) {
      uuid = uuidv4();
      Cookies.set('guest_uuid', uuid, { expires: 365 });
    }
    setGuestUuid(uuid);

    // Auth Token
    const token = Cookies.get('token');
    const savedUser = Cookies.get('user_info');
    if (token && savedUser) {
      try {
        setUser(JSON.parse(savedUser));
      } catch (e) {}
    }

    // Initial Fetch from server (always fetch on load/refresh)
    handleHeartbeat(uuid, 0); // Send 0 to just get stats

    // Single Heartbeat Interval (12 seconds)
    const heartbeatInterval = setInterval(() => {
      handleHeartbeat(uuid, localCountRef.current);
    }, 12 * 1000);

    // Save on close
    const handleBeforeUnload = () => {
       if (localCountRef.current > 0) {
           heartbeat(localCountRef.current, uuid);
       }
    };
    window.addEventListener('beforeunload', handleBeforeUnload);

    return () => {
      clearInterval(heartbeatInterval);
      window.removeEventListener('beforeunload', handleBeforeUnload);
    };
  }, []);

  const updateStats = (newStats) => {
    if (!newStats || typeof newStats.all_time_total !== 'number') {
        console.warn("Received invalid stats:", newStats);
        return;
    }

    setStats(prevStats => {
        // Monotonic Check: Ensure total never decreases from server side
        if (newStats.all_time_total < prevStats.all_time_total) {
            console.warn("Ignored outdated/lower stats:", newStats, "Current:", prevStats);
            return prevStats;
        }
        
        Cookies.set('stats', JSON.stringify(newStats), { expires: 365 });
        return newStats;
    });
  };

  const handleHeartbeat = async (uuid, countToSync) => {
    try {
      // Unified API: send count (even if 0) and get updated stats
      const newStats = await heartbeat(countToSync, uuid);
      
      if (countToSync > 0) {
        // We sent data, so subtract what we sent from local count
        setLocalCount(prev => Math.max(0, prev - countToSync));
      }
      
      // Update stats (which now includes the count we just sent)
      updateStats(newStats);
      
    } catch (err) {
      console.error("Heartbeat failed", err.response?.data || err.message || err);
    }
  };

  const handleClick = () => {
    setLocalCount(prev => prev + 1);
    
    // Trigger brightness animation
    setIsClicked(true);
    setTimeout(() => {
      setIsClicked(false);
    }, 800); // Animation duration
  };

  const handleAuthSubmit = async (e) => {
    e.preventDefault();
    try {
      let data;
      if (authMode === 'login') {
        data = await login(username, password);
      } else {
        data = await register(username, password);
      }
      
      Cookies.set('token', data.access_token, { expires: 30 });
      const userInfo = { username: data.username, id: data.user_id };
      Cookies.set('user_info', JSON.stringify(userInfo), { expires: 30 });
      setUser(userInfo);
      setShowAuth(false);
      
      // Immediate sync on login
      await handleHeartbeat(guestUuid, localCountRef.current);
      
    } catch (err) {
      alert(err.response?.data?.detail || "Authentication failed");
    }
  };

  const logout = () => {
    const count = localCountRef.current;
    if (count > 0) {
        heartbeat(count, guestUuid).then(() => {
            Cookies.remove('token');
            Cookies.remove('user_info');
            setUser(null);
            setLocalCount(0);
            handleHeartbeat(guestUuid, 0);
        });
    } else {
        Cookies.remove('token');
        Cookies.remove('user_info');
        setUser(null);
        handleHeartbeat(guestUuid, 0);
    }
  };

  // Render Helpers
  const displayToday = stats.today_total + localCount;
  const displayTotal = stats.all_time_total + localCount;

  return (
    <div className="App">
      {!user ? (
        <button className="auth-btn" onClick={() => setShowAuth(true)}>
          ورود / ثبت نام
        </button>
      ) : (
        <button className="auth-btn" onClick={logout}>
          خروج ({user.username})
        </button>
      )}

      <div className="salavat-container">
        <button 
          className={`salavat-btn ${isClicked ? 'clicked' : ''}`}
          onClick={handleClick}
        >
          <img 
            src={salavatImage} 
            alt="Salavat" 
            className="salavat-image"
          />
          {localCount > 0 && (
            <div className="click-feedback">
               (+{localCount})
            </div>
          )}
        </button>

        <div className="counter-display">
          <div className="stat-item">
            <span className="stat-label">تعداد امروز</span>
            <span className="stat-value">
              <AnimatedCounter value={displayToday} duration={5000} />
            </span>
          </div>
          
          <div className="divider"></div>
          
          <div className="stat-item">
            <span className="stat-label">تعداد کل</span>
            <span className="stat-value">
              <AnimatedCounter value={displayTotal} duration={5000} />
            </span>
          </div>
        </div>
      </div>
      
      <div className="user-share">
        {user ? `سهم شما: ${(stats.user_total + localCount).toLocaleString('fa-IR')}` : ''}
      </div>

      {showAuth && (
        <div className="auth-modal">
          <div className="modal-content">
            <button className="close-btn" onClick={() => setShowAuth(false)}>×</button>
            <h2>{authMode === 'login' ? 'ورود به حساب' : 'ثبت نام'}</h2>
            
            <form onSubmit={handleAuthSubmit}>
              <div className="input-group">
                <label>نام کاربری (موبایل/ایمیل)</label>
                <input 
                  type="text" 
                  value={username} 
                  onChange={e => setUsername(e.target.value)}
                  required 
                />
              </div>
              
              <div className="input-group">
                <label>رمز عبور</label>
                <input 
                  type="password" 
                  value={password} 
                  onChange={e => setPassword(e.target.value)}
                  required 
                />
              </div>

              <button type="submit" className="submit-btn">
                {authMode === 'login' ? 'ورود' : 'ثبت نام و ورود'}
              </button>
            </form>

            <div style={{marginTop: '15px', fontSize: '0.9rem', cursor: 'pointer', color: '#80cbc4'}}
                 onClick={() => setAuthMode(authMode === 'login' ? 'register' : 'login')}>
              {authMode === 'login' ? 'حساب ندارید؟ ثبت نام کنید' : 'حساب دارید؟ وارد شوید'}
            </div>
          </div>
        </div>
      )}
    </div>
  );
}

export default App;
