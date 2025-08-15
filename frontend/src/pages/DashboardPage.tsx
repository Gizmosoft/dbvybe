import { useState, useEffect } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import {
  Database,
  Plus,
  Settings,
  BarChart3,
  MessageSquare,
  Users,
  Activity,
  Zap,
  CheckCircle,
  AlertCircle,
  Clock,
  ArrowRight,
  Trash2,
  CircleSlash2,
  Play,
  Edit,
  Eye,
  Brain,
  LogOut,
  X,
  Server,
  User,
  Key
} from 'lucide-react';

interface DatabaseConnection {
  id: string;
  name: string;
  type: 'mysql' | 'postgresql' | 'mongodb';
  host: string;
  port: number;
  database: string;
  status: 'connected' | 'disconnected' | 'error';
  lastSync: string;
  tables: number;
  size: string;
}

const DashboardPage = () => {
  const [showAddConnection, setShowAddConnection] = useState(false);
  const [selectedDatabase, setSelectedDatabase] = useState<string | null>(null);
  const [user, setUser] = useState<any>(null);
  const [isConnecting, setIsConnecting] = useState(false);
  const navigate = useNavigate();

  // Form state for new connection
  const [connectionForm, setConnectionForm] = useState({
    name: '',
    type: 'postgresql',
    host: '',
    port: '',
    databaseName: '',
    username: '',
    password: ''
  });

  // Check if user is logged in and fetch connections
  useEffect(() => {
    const userData = localStorage.getItem('user');
    if (!userData) {
      navigate('/login');
      return;
    }
    const user = JSON.parse(userData);
    setUser(user);

    // Fetch user connections
    fetchUserConnections(user.userId);
  }, [navigate]);

  const fetchUserConnections = async (userId: string) => {
    try {
      setIsLoadingConnections(true);
      const response = await fetch(`/api/database/connections?userId=${userId}`);
      const data = await response.json();

      if (response.ok && data.success) {
        // Transform backend data to frontend format
        const transformedConnections: DatabaseConnection[] = data.connections.map((conn: any) => ({
          id: conn.connectionId,
          name: conn.connectionName,
          type: conn.databaseType.toLowerCase() as 'mysql' | 'postgresql' | 'mongodb',
          host: conn.host,
          port: conn.port,
          database: conn.databaseName,
          status: 'connected', // Default status since we don't have real-time status
          lastSync: conn.lastUsedAt ? new Date(conn.lastUsedAt).toLocaleString() : 'Never',
          tables: 0, // Will be updated when we get actual data
          size: '0 MB' // Will be updated when we get actual data
        }));

        setConnections(transformedConnections);
      } else {
        console.error('Failed to fetch connections:', data.message);
        setConnections([]);
      }
    } catch (error) {
      console.error('Error fetching connections:', error);
      setConnections([]);
    } finally {
      setIsLoadingConnections(false);
    }
  };

  const handleLogout = async () => {
    try {
      const userData = JSON.parse(localStorage.getItem('user') || '{}');

      // Call logout API
      const response = await fetch('/api/auth/logout', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
        },
        body: JSON.stringify({
          sessionId: userData.sessionId,
          action: 'LOGOUT'
        }),
      });

      // Clear localStorage regardless of API response
      localStorage.removeItem('user');
      sessionStorage.clear();

      // Redirect to home page
      navigate('/');
    } catch (error) {
      console.error('Logout error:', error);
      // Still clear storage and redirect even if API fails
      localStorage.removeItem('user');
      sessionStorage.clear();
      navigate('/');
    }
  };

  // User database connections from API
  const [connections, setConnections] = useState<DatabaseConnection[]>([]);
  const [isLoadingConnections, setIsLoadingConnections] = useState(true);

  const getStatusColor = (status: string) => {
    switch (status) {
      case 'connected':
        return 'var(--semantic-success)';
      case 'error':
        return 'var(--semantic-error)';
      case 'disconnected':
        return 'var(--semantic-warning)';
      default:
        return 'var(--neutral-gray)';
    }
  };

  const getStatusIcon = (status: string) => {
    switch (status) {
      case 'connected':
        return <CheckCircle size={16} />;
      case 'error':
        return <AlertCircle size={16} />;
      case 'disconnected':
        return <Clock size={16} />;
      default:
        return <Clock size={16} />;
    }
  };

  const getDatabaseIcon = (type: string) => {
    switch (type) {
      case 'mysql':
        return '🐬';
      case 'postgresql':
        return '🐘';
      case 'mongodb':
        return '🍃';
      default:
        return '🗄️';
    }
  };

  const handleDisableConnection = async (id: string) => {
    try {
      const userData = JSON.parse(localStorage.getItem('user') || '{}');
      
      console.log('Attempting to disable connection:', { connectionId: id, userId: userData.userId });
      
      // Call backend API to disable the connection
      const response = await fetch(`/api/database/connect/${id}?userId=${userData.userId}`, {
        method: 'DELETE',
        headers: {
          'Content-Type': 'application/json',
        },
      });

      const data = await response.json();
      console.log('Disable connection response:', { status: response.status, data });

      if (response.ok && data.success) {
        // Update the connection status in UI to show it's disabled
        setConnections(prevConnections => 
          prevConnections.map(conn => 
            conn.id === id 
              ? { ...conn, status: 'disconnected' as const }
              : conn
          )
        );
        console.log('Connection disabled successfully');
        alert('Connection disabled successfully!');
      } else {
        console.error('Failed to disable connection:', data.message);
        alert(`Failed to disable connection: ${data.message || 'Unknown error'}`);
      }
    } catch (error) {
      console.error('Error disabling connection:', error);
      alert(`Failed to disable connection: ${error instanceof Error ? error.message : 'Network error'}`);
    }
  };

  const handleEnableConnection = async (id: string) => {
    try {
      const userData = JSON.parse(localStorage.getItem('user') || '{}');
      
      console.log('Attempting to enable connection:', { connectionId: id, userId: userData.userId });
      
      // Call backend API to enable the connection
      const response = await fetch('/api/database/connect-saved', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
        },
        body: JSON.stringify({
          connectionId: id,
          userId: userData.userId
        }),
      });

      const data = await response.json();
      console.log('Enable connection response:', { status: response.status, data });

      if (response.ok && data.success) {
        // Update the connection status in UI to show it's connected
        setConnections(prevConnections => 
          prevConnections.map(conn => 
            conn.id === id 
              ? { ...conn, status: 'connected' as const }
              : conn
          )
        );
        console.log('Connection enabled successfully');
        alert('Connection enabled successfully!');
      } else {
        console.error('Failed to enable connection:', data.message);
        alert(`Failed to enable connection: ${data.message || 'Unknown error'}`);
      }
    } catch (error) {
      console.error('Error enabling connection:', error);
      alert(`Failed to enable connection: ${error instanceof Error ? error.message : 'Network error'}`);
    }
  };
  const handleDeleteConnection = async (id: string) => {
    try {
      const userData = JSON.parse(localStorage.getItem('user') || '{}');
      
      // Call backend API to delete the connection
      const response = await fetch(`/api/database/saved/${id}?userId=${userData.userId}`, {
        method: 'DELETE',
        headers: {
          'Content-Type': 'application/json',
        },
      });

      const data = await response.json();

      if (response.ok && data.success) {
        // Remove from UI only if backend deletion was successful
        setConnections(connections.filter(conn => conn.id !== id));
        console.log('Connection deleted successfully');
      } else {
        console.error('Failed to delete connection:', data.message);
        alert('Failed to delete connection. Please try again.');
      }
    } catch (error) {
      console.error('Error deleting connection:', error);
      alert('Failed to delete connection. Please try again.');
    }
  };

  const handleFormChange = (e: React.ChangeEvent<HTMLInputElement | HTMLSelectElement>) => {
    setConnectionForm({
      ...connectionForm,
      [e.target.name]: e.target.value
    });
  };

  const handleAddConnection = async (e: React.FormEvent) => {
    e.preventDefault();
    setIsConnecting(true);

    try {
      const userData = JSON.parse(localStorage.getItem('user') || '{}');

      const response = await fetch('/api/database/connect', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
        },
        body: JSON.stringify({
          userId: userData.userId,
          connectionName: connectionForm.name,
          databaseType: connectionForm.type.toUpperCase(),
          host: connectionForm.host,
          port: parseInt(connectionForm.port),
          databaseName: connectionForm.databaseName,
          username: connectionForm.username,
          password: connectionForm.password
        }),
      });

      const data = await response.json();

      if (response.ok && data.success) {
        // Add new connection to the list
        const newConnection: DatabaseConnection = {
          id: data.connectionId,
          name: connectionForm.name,
          type: connectionForm.type as 'mysql' | 'postgresql' | 'mongodb',
          host: connectionForm.host,
          port: parseInt(connectionForm.port),
          database: connectionForm.databaseName,
          status: 'connected',
          lastSync: 'Just now',
          tables: 0, // Will be updated when we get actual data
          size: '0 MB'
        };

        // Refresh connections list from API
        await fetchUserConnections(userData.userId);

        // Reset form and close popup
        setConnectionForm({
          name: '',
          type: 'postgresql',
          host: '',
          port: '',
          databaseName: '',
          username: '',
          password: ''
        });
        setShowAddConnection(false);

        alert('Database connection established successfully!');
      } else {
        alert(data.message || 'Failed to establish connection');
      }
    } catch (error) {
      console.error('Connection error:', error);
      alert('Failed to establish connection. Please try again.');
    } finally {
      setIsConnecting(false);
    }
  };

  const closePopup = () => {
    setShowAddConnection(false);
    setConnectionForm({
      name: '',
      type: 'postgresql',
      host: '',
      port: '',
      databaseName: '',
      username: '',
      password: ''
    });
  };

  return (
    <div className="min-h-screen" style={{ background: '#f8fafc' }}>
      <style>
        {`
          @keyframes spin {
            0% { transform: rotate(0deg); }
            100% { transform: rotate(360deg); }
          }
        `}
      </style>
      {/* Navigation */}
      <nav className="nav">
        <div className="nav-container">
          <Link to="/" className="nav-logo">
            <Database size={32} />
            dbVybe
          </Link>
          <ul className="nav-menu">
            <li><Link to="/" className="nav-menu-item">Home</Link></li>
            <li><Link to="/dashboard" className="nav-menu-item" style={{ color: 'var(--primary-purple)' }}>Dashboard</Link></li>
            {user ? (
              <li>
                <button
                  onClick={handleLogout}
                  className="btn btn-ghost"
                  style={{
                    display: 'flex',
                    alignItems: 'center',
                    gap: '8px',
                    color: 'var(--semantic-error)'
                  }}
                >
                  <LogOut size={16} />
                  Logout
                </button>
              </li>
            ) : (
              <li><Link to="/login" className="nav-menu-item">Login</Link></li>
            )}
          </ul>
        </div>
      </nav>

      <div className="container" style={{ paddingTop: '32px', paddingBottom: '32px' }}>
        {/* Header */}
        <div style={{ marginBottom: '32px' }}>
          <h1 style={{
            fontSize: 'var(--font-size-3xl)',
            fontWeight: 'var(--font-weight-bold)',
            marginBottom: '8px'
          }}>
            Welcome back, {user?.username || 'User'}!
          </h1>
          <p style={{ color: 'var(--neutral-gray)', fontSize: 'var(--font-size-lg)' }}>
            Manage and explore your database connections
          </p>
        </div>

        {/* Stats Cards */}
        <div style={{
          display: 'grid',
          gridTemplateColumns: 'repeat(auto-fit, minmax(250px, 1fr))',
          gap: '24px',
          marginBottom: '32px'
        }}>
          <div className="card">
            <div style={{ display: 'flex', alignItems: 'center', gap: '16px' }}>
              <div style={{
                width: '48px',
                height: '48px',
                background: 'var(--gradient-pink)',
                borderRadius: 'var(--border-radius-lg)',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center'
              }}>
                <Database size={24} color="white" />
              </div>
              <div>
                <p style={{ color: 'var(--neutral-gray)', fontSize: 'var(--font-size-sm)' }}>
                  Total Connections
                </p>
                <p style={{ fontSize: 'var(--font-size-2xl)', fontWeight: 'var(--font-weight-bold)' }}>
                  {connections.length}
                </p>
              </div>
            </div>
          </div>

          <div className="card">
            <div style={{ display: 'flex', alignItems: 'center', gap: '16px' }}>
              <div style={{
                width: '48px',
                height: '48px',
                background: 'var(--gradient-blue)',
                borderRadius: 'var(--border-radius-lg)',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center'
              }}>
                <CheckCircle size={24} color="white" />
              </div>
              <div>
                <p style={{ color: 'var(--neutral-gray)', fontSize: 'var(--font-size-sm)' }}>
                  Connected
                </p>
                <p style={{ fontSize: 'var(--font-size-2xl)', fontWeight: 'var(--font-weight-bold)' }}>
                  {connections.filter(c => c.status === 'connected').length}
                </p>
              </div>
            </div>
          </div>

          {/* <div className="card">
            <div style={{ display: 'flex', alignItems: 'center', gap: '16px' }}>
              <div style={{ 
                width: '48px', 
                height: '48px', 
                background: 'var(--gradient-pink)',
                borderRadius: 'var(--border-radius-lg)',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center'
              }}>
                <BarChart3 size={24} color="white" />
              </div>
              <div>
                <p style={{ color: 'var(--neutral-gray)', fontSize: 'var(--font-size-sm)' }}>
                  Total Tables
                </p>
                <p style={{ fontSize: 'var(--font-size-2xl)', fontWeight: 'var(--font-weight-bold)' }}>
                  {connections.reduce((sum, conn) => sum + conn.tables, 0)}
                </p>
              </div>
            </div>
          </div>

          <div className="card">
            <div style={{ display: 'flex', alignItems: 'center', gap: '16px' }}>
              <div style={{ 
                width: '48px', 
                height: '48px', 
                background: 'var(--gradient-vibrant)',
                borderRadius: 'var(--border-radius-lg)',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center'
              }}>
                <Zap size={24} color="white" />
              </div>
              <div>
                <p style={{ color: 'var(--neutral-gray)', fontSize: 'var(--font-size-sm)' }}>
                  Total Size
                </p>
                <p style={{ fontSize: 'var(--font-size-2xl)', fontWeight: 'var(--font-weight-bold)' }}>
                  5.1 GB
                </p>
              </div>
            </div>
          </div> */}
        </div>

        {/* Database Connections */}
        <div className="card" style={{ marginBottom: '32px' }}>
          <div style={{
            display: 'flex',
            justifyContent: 'space-between',
            alignItems: 'center',
            marginBottom: '24px'
          }}>
            <h2 style={{
              fontSize: 'var(--font-size-xl)',
              fontWeight: 'var(--font-weight-bold)'
            }}>
              Database Connections
            </h2>
            <button
              className="btn btn-primary"
              onClick={() => setShowAddConnection(true)}
            >
              <Plus size={20} />
              Add Connection
            </button>
          </div>

          <div style={{
            display: 'grid',
            gridTemplateColumns: 'repeat(auto-fit, minmax(350px, 1fr))',
            gap: '20px'
          }}>
            {isLoadingConnections ? (
              <div style={{
                gridColumn: '1 / -1',
                textAlign: 'center',
                padding: '40px',
                color: 'var(--neutral-gray)'
              }}>
                <div style={{ marginBottom: '16px' }}>
                  <div style={{
                    width: '40px',
                    height: '40px',
                    border: '3px solid var(--neutral-light-gray)',
                    borderTop: '3px solid var(--primary-purple)',
                    borderRadius: '50%',
                    animation: 'spin 1s linear infinite',
                    margin: '0 auto'
                  }}></div>
                </div>
                Loading connections...
              </div>
            ) : connections.length === 0 ? (
              <div style={{
                gridColumn: '1 / -1',
                textAlign: 'center',
                padding: '40px',
                color: 'var(--neutral-gray)'
              }}>
                <Database size={48} style={{ marginBottom: '16px', opacity: 0.5 }} />
                <h3 style={{ marginBottom: '8px', fontSize: 'var(--font-size-lg)' }}>
                  No database connections yet
                </h3>
                <p style={{ fontSize: 'var(--font-size-sm)' }}>
                  Add your first database connection to get started
                </p>
              </div>
            ) : (
              connections.map((connection) => (
                <div
                  key={connection.id}
                  className="card translate-hover"
                  style={{
                    border: selectedDatabase === connection.id ? '2px solid var(--primary-purple)' : '1px solid var(--neutral-light-gray)',
                    cursor: 'pointer'
                  }}
                  onClick={() => setSelectedDatabase(connection.id)}
                >
                  <div style={{
                    display: 'flex',
                    justifyContent: 'space-between',
                    alignItems: 'flex-start',
                    marginBottom: '16px'
                  }}>
                    <div style={{ display: 'flex', alignItems: 'center', gap: '12px' }}>
                      <span style={{ fontSize: '24px' }}>{getDatabaseIcon(connection.type)}</span>
                      <div>
                        <h3 style={{
                          fontSize: 'var(--font-size-lg)',
                          fontWeight: 'var(--font-weight-semibold)',
                          marginBottom: '4px'
                        }}>
                          {connection.name}
                        </h3>
                        <p style={{
                          fontSize: 'var(--font-size-sm)',
                          color: 'var(--neutral-gray)',
                          textTransform: 'capitalize'
                        }}>
                          {connection.type}
                        </p>
                      </div>
                    </div>
                    <div style={{ display: 'flex', gap: '8px' }}>
                      {/* <button
                        className="btn btn-ghost"
                        style={{ padding: '4px' }}
                        onClick={(e) => {
                          e.stopPropagation();
                          // Handle view
                        }}
                      >
                        <Eye size={16} />
                      </button>
                      <button
                        className="btn btn-ghost"
                        style={{ padding: '4px' }}
                        onClick={(e) => {
                          e.stopPropagation();
                          // Handle edit
                        }}
                      >
                        <Edit size={16} />
                      </button> */}
                      <button
                        className="btn btn-ghost"
                        style={{ 
                          padding: '4px', 
                          color: connection.status === 'connected' ? '#ff8c00' : '#22c55e' 
                        }}
                        onClick={(e) => {
                          e.stopPropagation();
                          if (connection.status === 'connected') {
                            handleDisableConnection(connection.id);
                          } else {
                            handleEnableConnection(connection.id);
                          }
                        }}
                      >
                        {connection.status === 'connected' ? (
                          <CircleSlash2 size={16} />
                        ) : (
                          <Play size={16} />
                        )}
                      </button>
                      <button
                        className="btn btn-ghost"
                        style={{ padding: '4px', color: 'var(--semantic-error)' }}
                        onClick={(e) => {
                          e.stopPropagation();
                          handleDeleteConnection(connection.id);
                        }}
                      >
                        <Trash2 size={16} />
                      </button>
                    </div>
                  </div>

                  <div style={{
                    display: 'flex',
                    alignItems: 'center',
                    gap: '8px',
                    marginBottom: '12px'
                  }}>
                    {getStatusIcon(connection.status)}
                    <span style={{
                      fontSize: 'var(--font-size-sm)',
                      color: getStatusColor(connection.status)
                    }}>
                      {connection.status}
                    </span>
                  </div>

                  <div style={{
                    display: 'grid',
                    gridTemplateColumns: '1fr 1fr',
                    gap: '12px',
                    fontSize: 'var(--font-size-sm)'
                  }}>
                    <div>
                      <p style={{ color: 'var(--neutral-gray)', marginBottom: '4px' }}>Host</p>
                      <p style={{ fontWeight: 'var(--font-weight-medium)' }}>
                        {connection.host}:{connection.port}
                      </p>
                    </div>
                    <div>
                      <p style={{ color: 'var(--neutral-gray)', marginBottom: '4px' }}>Database</p>
                      <p style={{ fontWeight: 'var(--font-weight-medium)' }}>
                        {connection.database}
                      </p>
                    </div>
                    {/* <div>
                    <p style={{ color: 'var(--neutral-gray)', marginBottom: '4px' }}>Tables</p>
                    <p style={{ fontWeight: 'var(--font-weight-medium)' }}>
                      {connection.tables}
                    </p>
                  </div>
                  <div>
                    <p style={{ color: 'var(--neutral-gray)', marginBottom: '4px' }}>Size</p>
                    <p style={{ fontWeight: 'var(--font-weight-medium)' }}>
                      {connection.size}
                    </p>
                  </div> */}
                  </div>

                                     <div style={{
                     marginTop: '16px',
                     paddingTop: '16px',
                     borderTop: '1px solid var(--neutral-light-gray)',
                     display: 'flex',
                     justifyContent: 'flex-end',
                     alignItems: 'center'
                   }}>
                     <Link
                       to={`/explore?db=${connection.id}`}
                       className="btn btn-ghost"
                       style={{
                         fontSize: 'var(--font-size-sm)',
                         padding: '4px 8px'
                       }}
                     >
                       Explore
                       <ArrowRight size={16} />
                     </Link>
                   </div>
                </div>
              ))
            )}
          </div>
        </div>

        {/* Quick Actions */}
        <div className="card">
          <h2 style={{
            fontSize: 'var(--font-size-xl)',
            fontWeight: 'var(--font-weight-bold)',
            marginBottom: '24px'
          }}>
            Quick Actions
          </h2>
          <div style={{
            display: 'grid',
            gridTemplateColumns: 'repeat(auto-fit, minmax(200px, 1fr))',
            gap: '16px'
          }}>
            <Link to="/explore" className="card translate-hover" style={{
              textDecoration: 'none',
              color: 'inherit',
              textAlign: 'center',
              padding: '24px'
            }}>
              <Brain size={32} style={{
                color: 'var(--primary-purple)',
                marginBottom: '12px'
              }} />
              <h3 style={{
                fontSize: 'var(--font-size-lg)',
                fontWeight: 'var(--font-weight-semibold)',
                marginBottom: '8px'
              }}>
                Ask Questions
              </h3>
              <p style={{
                fontSize: 'var(--font-size-sm)',
                color: 'var(--neutral-gray)'
              }}>
                Use natural language to query your databases
              </p>
            </Link>

            <Link to="/explore" className="card translate-hover" style={{
              textDecoration: 'none',
              color: 'inherit',
              textAlign: 'center',
              padding: '24px'
            }}>
              <BarChart3 size={32} style={{
                color: 'var(--secondary-blue)',
                marginBottom: '12px'
              }} />
              <h3 style={{
                fontSize: 'var(--font-size-lg)',
                fontWeight: 'var(--font-weight-semibold)',
                marginBottom: '8px'
              }}>
                Create Reports
              </h3>
              <p style={{
                fontSize: 'var(--font-size-sm)',
                color: 'var(--neutral-gray)'
              }}>
                Generate visualizations and insights
              </p>
            </Link>

            <Link to="/explore" className="card translate-hover" style={{
              textDecoration: 'none',
              color: 'inherit',
              textAlign: 'center',
              padding: '24px'
            }}>
              <MessageSquare size={32} style={{
                color: 'var(--secondary-pink)',
                marginBottom: '12px'
              }} />
              <h3 style={{
                fontSize: 'var(--font-size-lg)',
                fontWeight: 'var(--font-weight-semibold)',
                marginBottom: '8px'
              }}>
                Share Insights
              </h3>
              <p style={{
                fontSize: 'var(--font-size-sm)',
                color: 'var(--neutral-gray)'
              }}>
                Collaborate with your team
              </p>
            </Link>

            <Link to="/explore" className="card translate-hover" style={{
              textDecoration: 'none',
              color: 'inherit',
              textAlign: 'center',
              padding: '24px'
            }}>
              <Activity size={32} style={{
                color: 'var(--semantic-info)',
                marginBottom: '12px'
              }} />
              <h3 style={{
                fontSize: 'var(--font-size-lg)',
                fontWeight: 'var(--font-weight-semibold)',
                marginBottom: '8px'
              }}>
                Monitor Performance
              </h3>
              <p style={{
                fontSize: 'var(--font-size-sm)',
                color: 'var(--neutral-gray)'
              }}>
                Track query performance and usage
              </p>
            </Link>
          </div>
        </div>
      </div>

      {/* Add Connection Popup */}
      {showAddConnection && (
        <div style={{
          position: 'fixed',
          top: 0,
          left: 0,
          right: 0,
          bottom: 0,
          background: 'rgba(0, 0, 0, 0.5)',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          zIndex: 1000,
          backdropFilter: 'blur(4px)'
        }}>
          <div className="card" style={{
            maxWidth: '500px',
            width: '90%',
            maxHeight: '90vh',
            overflowY: 'auto',
            position: 'relative'
          }}>
            {/* Header */}
            <div style={{
              display: 'flex',
              justifyContent: 'space-between',
              alignItems: 'center',
              marginBottom: '24px',
              paddingBottom: '16px',
              borderBottom: '1px solid var(--neutral-light-gray)'
            }}>
              <h2 style={{
                fontSize: 'var(--font-size-xl)',
                fontWeight: 'var(--font-weight-bold)',
                margin: 0
              }}>
                Add Database Connection
              </h2>
              <button
                onClick={closePopup}
                className="btn btn-ghost"
                style={{ padding: '8px' }}
              >
                <X size={20} />
              </button>
            </div>

            {/* Form */}
            <form onSubmit={handleAddConnection}>
              <div style={{ display: 'grid', gap: '20px' }}>
                {/* Connection Name */}
                <div className="form-group">
                  <label htmlFor="name" className="form-label">
                    Connection Name
                  </label>
                  <div style={{ position: 'relative' }}>
                    <Database size={20} style={{
                      position: 'absolute',
                      left: '12px',
                      top: '50%',
                      transform: 'translateY(-50%)',
                      color: 'var(--neutral-gray)'
                    }} />
                    <input
                      type="text"
                      id="name"
                      name="name"
                      value={connectionForm.name}
                      onChange={handleFormChange}
                      className="form-input"
                      style={{ paddingLeft: '44px' }}
                      placeholder="e.g., Production Database"
                      required
                    />
                  </div>
                </div>

                {/* Database Type */}
                <div className="form-group">
                  <label htmlFor="type" className="form-label">
                    Database Type
                  </label>
                  <select
                    id="type"
                    name="type"
                    value={connectionForm.type}
                    onChange={handleFormChange}
                    className="form-input"
                    required
                  >
                    <option value="postgresql">PostgreSQL</option>
                    <option value="mysql">MySQL</option>
                    <option value="mongodb">MongoDB</option>
                  </select>
                </div>

                {/* Host and Port */}
                <div style={{ display: 'grid', gridTemplateColumns: '1fr 120px', gap: '16px' }}>
                  <div className="form-group">
                    <label htmlFor="host" className="form-label">
                      Host
                    </label>
                    <div style={{ position: 'relative' }}>
                      <Server size={20} style={{
                        position: 'absolute',
                        left: '12px',
                        top: '50%',
                        transform: 'translateY(-50%)',
                        color: 'var(--neutral-gray)'
                      }} />
                      <input
                        type="text"
                        id="host"
                        name="host"
                        value={connectionForm.host}
                        onChange={handleFormChange}
                        className="form-input"
                        style={{ paddingLeft: '44px' }}
                        placeholder="localhost"
                        required
                      />
                    </div>
                  </div>

                  <div className="form-group">
                    <label htmlFor="port" className="form-label">
                      Port
                    </label>
                    <input
                      type="number"
                      id="port"
                      name="port"
                      value={connectionForm.port}
                      onChange={handleFormChange}
                      className="form-input"
                      placeholder={connectionForm.type === 'postgresql' ? '5432' : connectionForm.type === 'mysql' ? '3306' : '27017'}
                      required
                    />
                  </div>
                </div>

                {/* Database Name */}
                <div className="form-group">
                  <label htmlFor="databaseName" className="form-label">
                    Database Name
                  </label>
                  <div style={{ position: 'relative' }}>
                    <Database size={20} style={{
                      position: 'absolute',
                      left: '12px',
                      top: '50%',
                      transform: 'translateY(-50%)',
                      color: 'var(--neutral-gray)'
                    }} />
                    <input
                      type="text"
                      id="databaseName"
                      name="databaseName"
                      value={connectionForm.databaseName}
                      onChange={handleFormChange}
                      className="form-input"
                      style={{ paddingLeft: '44px' }}
                      placeholder="Enter database name"
                      required
                    />
                  </div>
                </div>

                {/* Username and Password */}
                <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '16px' }}>
                  <div className="form-group">
                    <label htmlFor="username" className="form-label">
                      Username
                    </label>
                    <div style={{ position: 'relative' }}>
                      <User size={20} style={{
                        position: 'absolute',
                        left: '12px',
                        top: '50%',
                        transform: 'translateY(-50%)',
                        color: 'var(--neutral-gray)'
                      }} />
                      <input
                        type="text"
                        id="username"
                        name="username"
                        value={connectionForm.username}
                        onChange={handleFormChange}
                        className="form-input"
                        style={{ paddingLeft: '44px' }}
                        placeholder="Enter username"
                        required
                      />
                    </div>
                  </div>

                  <div className="form-group">
                    <label htmlFor="password" className="form-label">
                      Password
                    </label>
                    <div style={{ position: 'relative' }}>
                      <Key size={20} style={{
                        position: 'absolute',
                        left: '12px',
                        top: '50%',
                        transform: 'translateY(-50%)',
                        color: 'var(--neutral-gray)'
                      }} />
                      <input
                        type="password"
                        id="password"
                        name="password"
                        value={connectionForm.password}
                        onChange={handleFormChange}
                        className="form-input"
                        style={{ paddingLeft: '44px' }}
                        placeholder="Enter password"
                        required
                      />
                    </div>
                  </div>
                </div>

                {/* Buttons */}
                <div style={{
                  display: 'flex',
                  gap: '12px',
                  justifyContent: 'flex-end',
                  marginTop: '24px',
                  paddingTop: '16px',
                  borderTop: '1px solid var(--neutral-light-gray)'
                }}>
                  <button
                    type="button"
                    onClick={closePopup}
                    className="btn btn-ghost"
                    disabled={isConnecting}
                  >
                    Cancel
                  </button>
                  <button
                    type="submit"
                    className="btn btn-primary"
                    disabled={isConnecting}
                  >
                    {isConnecting ? 'Connecting...' : 'Connect'}
                  </button>
                </div>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
};

export default DashboardPage; 