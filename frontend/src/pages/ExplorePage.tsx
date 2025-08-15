import { useState, useRef, useEffect } from 'react';
import { Link, useNavigate, useSearchParams } from 'react-router-dom';
import { 
  Database, 
  Send, 
  Brain, 
  BarChart3, 
  Download, 
  Share2, 
  Loader2,
  MessageSquare,
  Table,
  PieChart,
  LogOut
} from 'lucide-react';
import React from 'react'; // Added missing import for React

interface Message {
  id: string;
  type: 'user' | 'ai';
  content: string;
  timestamp: Date;
  data?: any;
  visualization?: any;
  query?: string; // Add query field for AI messages
}

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

const ExplorePage = () => {
  const [messages, setMessages] = useState<Message[]>([
    {
      id: '1',
      type: 'ai',
      content: "Hello! I'm Vybie. I can help you explore and analyze your databases. What would you like to know about your database?",
      timestamp: new Date()
    }
  ]);
  const [inputValue, setInputValue] = useState('');
  const [isLoading, setIsLoading] = useState(false);
  const [selectedDatabase, setSelectedDatabase] = useState<string>('');
  const [user, setUser] = useState<any>(null);
  const [connections, setConnections] = useState<DatabaseConnection[]>([]);
  const [isLoadingConnections, setIsLoadingConnections] = useState(true);
  const messagesEndRef = useRef<HTMLDivElement>(null);
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();

  // Check if user is logged in and fetch connections
  useEffect(() => {
    const userData = localStorage.getItem('user');
    if (!userData) {
      navigate('/login');
      return;
    }
    const user = JSON.parse(userData);
    setUser(user);

    // Check if a specific database is selected via URL parameter
    const dbParam = searchParams.get('db');
    if (dbParam) {
      setSelectedDatabase(dbParam);
    }

    // Fetch user connections
    fetchUserConnections(user.userId);
  }, [navigate, searchParams]);

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
        
        // If no database is selected and we have connections, select the first one
        if (!selectedDatabase && transformedConnections.length > 0) {
          setSelectedDatabase(transformedConnections[0].id);
        }
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

  const scrollToBottom = () => {
    messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' });
  };

  useEffect(() => {
    scrollToBottom();
  }, [messages]);

  const handleSendMessage = async () => {
    if (!inputValue.trim() || isLoading || !selectedDatabase) return;

    const userMessage: Message = {
      id: Date.now().toString(),
      type: 'user',
      content: inputValue,
      timestamp: new Date()
    };

    setMessages(prev => [...prev, userMessage]);
    setInputValue('');
    setIsLoading(true);

    try {
      // Call the actual chat API
      const userData = JSON.parse(localStorage.getItem('user') || '{}');
      
      const requestBody = {
        message: inputValue,
        sessionId: userData.sessionId || 'default-session',
        connectionId: selectedDatabase
      };

      console.log('Sending chat request:', {
        url: '/api/chat/database',
        body: requestBody,
        userId: userData.userId
      });

      const response = await fetch('/api/chat/database', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          'X-User-ID': userData.userId || 'anonymous'
        },
        body: JSON.stringify(requestBody),
      });

      console.log('Chat response status:', response.status);
      
      const data = await response.json();
      console.log('Chat response data:', data);

      if (response.ok && !data.error) {
        const aiMessage: Message = {
          id: (Date.now() + 1).toString(),
          type: 'ai',
          content: data.response,
          timestamp: new Date(),
          data: data.data,
          visualization: data.metadata?.visualizationType || null,
          query: data.query // Assuming the backend returns a 'query' field
        };

        setMessages(prev => [...prev, aiMessage]);
      } else {
        const aiMessage: Message = {
          id: (Date.now() + 1).toString(),
          type: 'ai',
          content: data.message || data.response || `Error: ${response.status} - ${response.statusText}`,
          timestamp: new Date()
        };

        setMessages(prev => [...prev, aiMessage]);
      }
    } catch (error) {
      console.error('Error sending message:', error);
      const aiMessage: Message = {
        id: (Date.now() + 1).toString(),
        type: 'ai',
        content: 'Sorry, I encountered an error while processing your request. Please try again.',
        timestamp: new Date()
      };

      setMessages(prev => [...prev, aiMessage]);
    } finally {
      setIsLoading(false);
    }
  };

  const handleKeyPress = (e: React.KeyboardEvent) => {
    if (e.key === 'Enter' && !e.shiftKey) {
      e.preventDefault();
      handleSendMessage();
    }
  };

  const copyToClipboard = (text: string) => {
    navigator.clipboard.writeText(text);
  };

  const formatMessageContent = (content: string) => {
    if (!content) return null;
    
    // Split content by newlines and create array of text and line breaks
    const parts = content.split('\n');
    
    return parts.map((part, index) => (
      <React.Fragment key={index}>
        {part}
        {index < parts.length - 1 && <br />}
      </React.Fragment>
    ));
  };

  const renderVisualization = (data: any, type: string) => {
    if (!data) return null;

    switch (type) {
      case 'bar-chart':
        return (
          <div className="card" style={{ marginTop: '16px' }}>
            <div style={{ display: 'flex', alignItems: 'center', gap: '8px', marginBottom: '16px' }}>
              <BarChart3 size={20} color="var(--primary-purple)" />
              <h4 style={{ fontWeight: 'var(--font-weight-semibold)' }}>Data Analysis</h4>
            </div>
            <div style={{ 
              display: 'grid', 
              gridTemplateColumns: 'repeat(auto-fit, minmax(120px, 1fr))',
              gap: '16px',
              marginTop: '16px'
            }}>
              {data.columnNames && data.rows && data.rows.length > 0 && (
                <div style={{ gridColumn: '1 / -1' }}>
                  <p style={{ fontSize: 'var(--font-size-sm)', color: 'var(--neutral-gray)', marginBottom: '8px' }}>
                    Query Results ({data.rows.length} rows)
                  </p>
                  <div style={{ 
                    maxHeight: '200px', 
                    overflowY: 'auto',
                    border: '1px solid var(--neutral-light-gray)',
                    borderRadius: 'var(--border-radius-md)'
                  }}>
                    <table style={{ width: '100%', borderCollapse: 'collapse' }}>
                      <thead style={{ background: 'var(--neutral-light-gray)' }}>
                        <tr>
                          {data.columnNames.map((col: string, index: number) => (
                            <th key={index} style={{ 
                              padding: '8px 12px', 
                              textAlign: 'left',
                              fontSize: 'var(--font-size-sm)',
                              fontWeight: 'var(--font-weight-medium)'
                            }}>
                              {col}
                            </th>
                          ))}
                        </tr>
                      </thead>
                      <tbody>
                        {data.rows.slice(0, 10).map((row: any[], rowIndex: number) => (
                          <tr key={rowIndex} style={{ borderBottom: '1px solid var(--neutral-light-gray)' }}>
                            {row.map((cell: any, cellIndex: number) => (
                              <td key={cellIndex} style={{ 
                                padding: '8px 12px',
                                fontSize: 'var(--font-size-sm)'
                              }}>
                                {cell !== null && cell !== undefined ? String(cell) : 'NULL'}
                              </td>
                            ))}
                          </tr>
                        ))}
                      </tbody>
                    </table>
                    {data.rows.length > 10 && (
                      <div style={{ 
                        padding: '8px 12px', 
                        background: 'var(--neutral-light-gray)',
                        fontSize: 'var(--font-size-sm)',
                        color: 'var(--neutral-gray)',
                        textAlign: 'center'
                      }}>
                        Showing first 10 rows of {data.rows.length} total rows
                      </div>
                    )}
                  </div>
                </div>
              )}
            </div>
          </div>
        );

      case 'pie-chart':
        return (
          <div className="card" style={{ marginTop: '16px' }}>
            <div style={{ display: 'flex', alignItems: 'center', gap: '8px', marginBottom: '16px' }}>
              <PieChart size={20} color="var(--secondary-blue)" />
              <h4 style={{ fontWeight: 'var(--font-weight-semibold)' }}>Data Analysis</h4>
            </div>
            <div style={{ 
              display: 'grid', 
              gridTemplateColumns: 'repeat(auto-fit, minmax(150px, 1fr))',
              gap: '16px'
            }}>
              {data.columnNames && data.rows && data.rows.length > 0 && (
                <div style={{ gridColumn: '1 / -1' }}>
                  <p style={{ fontSize: 'var(--font-size-sm)', color: 'var(--neutral-gray)', marginBottom: '8px' }}>
                    Query Results ({data.rows.length} rows)
                  </p>
                  <div style={{ 
                    maxHeight: '200px', 
                    overflowY: 'auto',
                    border: '1px solid var(--neutral-light-gray)',
                    borderRadius: 'var(--border-radius-md)'
                  }}>
                    <table style={{ width: '100%', borderCollapse: 'collapse' }}>
                      <thead style={{ background: 'var(--neutral-light-gray)' }}>
                        <tr>
                          {data.columnNames.map((col: string, index: number) => (
                            <th key={index} style={{ 
                              padding: '8px 12px', 
                              textAlign: 'left',
                              fontSize: 'var(--font-size-sm)',
                              fontWeight: 'var(--font-weight-medium)'
                            }}>
                              {col}
                            </th>
                          ))}
                        </tr>
                      </thead>
                      <tbody>
                        {data.rows.slice(0, 10).map((row: any[], rowIndex: number) => (
                          <tr key={rowIndex} style={{ borderBottom: '1px solid var(--neutral-light-gray)' }}>
                            {row.map((cell: any, cellIndex: number) => (
                              <td key={cellIndex} style={{ 
                                padding: '8px 12px',
                                fontSize: 'var(--font-size-sm)'
                              }}>
                                {cell !== null && cell !== undefined ? String(cell) : 'NULL'}
                              </td>
                            ))}
                          </tr>
                        ))}
                      </tbody>
                    </table>
                    {data.rows.length > 10 && (
                      <div style={{ 
                        padding: '8px 12px', 
                        background: 'var(--neutral-light-gray)',
                        fontSize: 'var(--font-size-sm)',
                        color: 'var(--neutral-gray)',
                        textAlign: 'center'
                      }}>
                        Showing first 10 rows of {data.rows.length} total rows
                      </div>
                    )}
                  </div>
                </div>
              )}
            </div>
          </div>
        );

      case 'table':
        return (
          <div className="card" style={{ marginTop: '16px' }}>
            <div style={{ display: 'flex', alignItems: 'center', gap: '8px', marginBottom: '16px' }}>
              <Table size={20} color="var(--secondary-pink)" />
              <h4 style={{ fontWeight: 'var(--font-weight-semibold)' }}>Data Analysis</h4>
            </div>
            <div style={{ 
              display: 'grid', 
              gridTemplateColumns: 'repeat(auto-fit, minmax(120px, 1fr))',
              gap: '16px'
            }}>
              {data.columnNames && data.rows && data.rows.length > 0 && (
                <div style={{ gridColumn: '1 / -1' }}>
                  <p style={{ fontSize: 'var(--font-size-sm)', color: 'var(--neutral-gray)', marginBottom: '8px' }}>
                    Query Results ({data.rows.length} rows)
                  </p>
                  <div style={{ 
                    maxHeight: '200px', 
                    overflowY: 'auto',
                    border: '1px solid var(--neutral-light-gray)',
                    borderRadius: 'var(--border-radius-md)'
                  }}>
                    <table style={{ width: '100%', borderCollapse: 'collapse' }}>
                      <thead style={{ background: 'var(--neutral-light-gray)' }}>
                        <tr>
                          {data.columnNames.map((col: string, index: number) => (
                            <th key={index} style={{ 
                              padding: '8px 12px', 
                              textAlign: 'left',
                              fontSize: 'var(--font-size-sm)',
                              fontWeight: 'var(--font-weight-medium)'
                            }}>
                              {col}
                            </th>
                          ))}
                        </tr>
                      </thead>
                      <tbody>
                        {data.rows.slice(0, 10).map((row: any[], rowIndex: number) => (
                          <tr key={rowIndex} style={{ borderBottom: '1px solid var(--neutral-light-gray)' }}>
                            {row.map((cell: any, cellIndex: number) => (
                              <td key={cellIndex} style={{ 
                                padding: '8px 12px',
                                fontSize: 'var(--font-size-sm)'
                              }}>
                                {cell !== null && cell !== undefined ? String(cell) : 'NULL'}
                              </td>
                            ))}
                          </tr>
                        ))}
                      </tbody>
                    </table>
                    {data.rows.length > 10 && (
                      <div style={{ 
                        padding: '8px 12px', 
                        background: 'var(--neutral-light-gray)',
                        fontSize: 'var(--font-size-sm)',
                        color: 'var(--neutral-gray)',
                        textAlign: 'center'
                      }}>
                        Showing first 10 rows of {data.rows.length} total rows
                      </div>
                    )}
                  </div>
                </div>
              )}
            </div>
          </div>
        );

      default:
        return null;
    }
  };

  return (
    <div className="min-h-screen" style={{ background: '#f8fafc' }}>
      {/* Navigation */}
      <nav className="nav">
        <div className="nav-container">
          <Link to="/" className="nav-logo">
            <Database size={32} />
            dbVybe
          </Link>
          <ul className="nav-menu">
            <li><Link to="/" className="nav-menu-item">Home</Link></li>
            {user && <li><Link to="/dashboard" className="nav-menu-item">Dashboard</Link></li>}
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
        <div style={{ 
          display: 'grid', 
          gridTemplateColumns: '1fr 300px',
          gap: '32px',
          height: 'calc(100vh - 200px)',
          maxHeight: 'calc(100vh - 200px)'
        }}>
          {/* Chat Interface */}
          <div className="card" style={{ 
            display: 'flex', 
            flexDirection: 'column',
            height: '100%',
            maxHeight: '100%',
            overflow: 'hidden'
          }}>
            <div style={{ 
              display: 'flex', 
              alignItems: 'center', 
              gap: '12px',
              paddingBottom: '16px',
              borderBottom: '1px solid var(--neutral-light-gray)',
              marginBottom: '16px',
              flexShrink: 0
            }}>
              <div style={{ 
                width: '40px', 
                height: '40px', 
                background: 'var(--gradient-primary)',
                borderRadius: 'var(--border-radius-lg)',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center'
              }}>
                <Brain size={20} color="white" />
              </div>
              <div>
                <h2 style={{ 
                  fontSize: 'var(--font-size-lg)', 
                  fontWeight: 'var(--font-weight-semibold)',
                  marginBottom: '4px'
                }}>
                  Vybie - AI Assistant
                </h2>
                <p style={{ 
                  fontSize: 'var(--font-size-sm)', 
                  color: 'var(--neutral-gray)'
                }}>
                  {selectedDatabase ? `Connected to: ${connections.find(c => c.id === selectedDatabase)?.name || 'Unknown Database'}` : 'Select a database to start chatting'}
                </p>
              </div>
            </div>

            {/* Messages */}
            <div style={{ 
              flex: 1, 
              overflowY: 'auto',
              paddingRight: '8px',
              minHeight: 0
            }}>
              {messages.map((message) => (
                <div key={message.id} style={{ marginBottom: '24px' }}>
                  <div style={{ 
                    display: 'flex', 
                    gap: '12px',
                    alignItems: 'flex-end',
                    justifyContent: message.type === 'user' ? 'flex-end' : 'flex-start'
                  }}>
                    {message.type === 'ai' && (
                      <div style={{ 
                        width: '32px', 
                        height: '32px', 
                        background: 'var(--gradient-primary)',
                        borderRadius: 'var(--border-radius-full)',
                        display: 'flex',
                        alignItems: 'center',
                        justifyContent: 'center',
                        flexShrink: 0
                      }}>
                        <Brain size={16} color="white" />
                      </div>
                    )}
                    <div style={{ 
                      maxWidth: '70%',
                      background: message.type === 'user' ? 'var(--primary-purple)' : 'var(--neutral-white)',
                      color: message.type === 'user' ? 'white' : 'var(--neutral-dark)',
                      padding: '12px 16px',
                      borderRadius: message.type === 'user' ? 'var(--border-radius-lg) 0 var(--border-radius-lg) var(--border-radius-lg)' : '0 var(--border-radius-lg) var(--border-radius-lg) var(--border-radius-lg)',
                      border: message.type === 'ai' ? '1px solid var(--neutral-light-gray)' : 'none',
                      boxShadow: message.type === 'ai' ? 'var(--shadow-sm)' : 'none',
                      marginLeft: message.type === 'user' ? 'auto' : '0'
                    }}>
                      {message.type === 'ai' && message.query && (
                        <div style={{ 
                          fontSize: 'var(--font-size-sm)', 
                          color: '#6b7280', // Dark grey but lighter than regular text
                          marginBottom: '8px',
                          fontFamily: 'monospace',
                          backgroundColor: '#f9fafb',
                          padding: '6px 8px',
                          borderRadius: 'var(--border-radius-sm)',
                          border: '1px solid #e5e7eb'
                        }}>
                          {formatMessageContent(message.query)}
                        </div>
                      )}
                      <p style={{ 
                        lineHeight: 'var(--line-height-relaxed)',
                        marginBottom: message.data ? '12px' : '0'
                      }}>
                        {formatMessageContent(message.content)}
                      </p>
                      {message.data && renderVisualization(message.data, message.visualization)}
                    </div>
                    {message.type === 'user' && (
                      <div style={{ 
                        width: '32px', 
                        height: '32px', 
                        background: 'var(--gradient-vibrant)',
                        borderRadius: 'var(--border-radius-full)',
                        display: 'flex',
                        alignItems: 'center',
                        justifyContent: 'center',
                        flexShrink: 0
                      }}>
                        <MessageSquare size={16} color="white" />
                      </div>
                    )}
                  </div>
                  <div style={{ 
                    fontSize: 'var(--font-size-xs)', 
                    color: 'var(--neutral-gray)',
                    marginTop: '8px',
                    marginLeft: message.type === 'user' ? 'auto' : '44px',
                    marginRight: message.type === 'user' ? '44px' : 'auto',
                    textAlign: message.type === 'user' ? 'right' : 'left'
                  }}>
                    {message.timestamp.toLocaleTimeString()}
                  </div>
                </div>
              ))}
              
              {isLoading && (
                <div style={{ marginBottom: '24px' }}>
                  <div style={{ display: 'flex', gap: '12px', alignItems: 'flex-start' }}>
                    <div style={{ 
                      width: '32px', 
                      height: '32px', 
                      background: 'var(--gradient-primary)',
                      borderRadius: 'var(--border-radius-full)',
                      display: 'flex',
                      alignItems: 'center',
                      justifyContent: 'center',
                      flexShrink: 0
                    }}>
                      <Brain size={16} color="white" />
                    </div>
                    <div style={{ 
                      background: 'var(--neutral-white)',
                      padding: '12px 16px',
                      borderRadius: '0 var(--border-radius-lg) var(--border-radius-lg) var(--border-radius-lg)',
                      border: '1px solid var(--neutral-light-gray)',
                      boxShadow: 'var(--shadow-sm)'
                    }}>
                      <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                        <Loader2 size={16} className="animate-spin" />
                        <span style={{ fontSize: 'var(--font-size-sm)' }}>Analyzing your data...</span>
                      </div>
                    </div>
                  </div>
                </div>
              )}
              
              <div ref={messagesEndRef} />
            </div>

            {/* Input */}
            <div style={{ 
              borderTop: '1px solid var(--neutral-light-gray)',
              paddingTop: '16px',
              flexShrink: 0
            }}>
              <div style={{ display: 'flex', gap: '12px' }}>
                <textarea
                  value={inputValue}
                  onChange={(e) => setInputValue(e.target.value)}
                  onKeyPress={handleKeyPress}
                  placeholder={selectedDatabase ? "Ask a question about your data..." : "Select a database to start chatting..."}
                  disabled={!selectedDatabase}
                  style={{
                    flex: 1,
                    padding: '12px 16px',
                    border: '2px solid var(--neutral-light-gray)',
                    borderRadius: 'var(--border-radius-lg)',
                    fontFamily: 'var(--font-family-primary)',
                    fontSize: 'var(--font-size-base)',
                    resize: 'none',
                    minHeight: '48px',
                    maxHeight: '120px',
                    lineHeight: 'var(--line-height-normal)',
                    opacity: selectedDatabase ? 1 : 0.6
                  }}
                />
                <button
                  onClick={handleSendMessage}
                  disabled={!inputValue.trim() || isLoading || !selectedDatabase}
                  className="btn btn-primary"
                  style={{ 
                    alignSelf: 'flex-end',
                    padding: '12px',
                    minWidth: '48px',
                    opacity: selectedDatabase ? 1 : 0.6
                  }}
                >
                  <Send size={20} />
                </button>
              </div>
            </div>
          </div>

          {/* Sidebar */}
          <div style={{ 
            display: 'flex', 
            flexDirection: 'column', 
            gap: '24px',
            height: '100%',
            maxHeight: '100%',
            overflowY: 'auto'
          }}>
            {/* Database Selector */}
            <div className="card">
              <h3 style={{ 
                fontSize: 'var(--font-size-lg)', 
                fontWeight: 'var(--font-weight-semibold)',
                marginBottom: '16px'
              }}>
                Connected Databases
              </h3>
              <div style={{ display: 'flex', flexDirection: 'column', gap: '12px' }}>
                {isLoadingConnections ? (
                  <div style={{ 
                    textAlign: 'center', 
                    padding: '20px',
                    color: 'var(--neutral-gray)'
                  }}>
                    <Loader2 size={20} className="animate-spin" style={{ marginBottom: '8px' }} />
                    <p style={{ fontSize: 'var(--font-size-sm)' }}>Loading connections...</p>
                  </div>
                ) : connections.length === 0 ? (
                  <div style={{ 
                    textAlign: 'center', 
                    padding: '20px',
                    color: 'var(--neutral-gray)'
                  }}>
                    <Database size={32} style={{ marginBottom: '8px', opacity: 0.5 }} />
                    <p style={{ fontSize: 'var(--font-size-sm)', marginBottom: '8px' }}>
                      No database connections
                    </p>
                    <Link 
                      to="/dashboard" 
                      className="btn btn-primary"
                      style={{ fontSize: 'var(--font-size-sm)', padding: '8px 16px' }}
                    >
                      Add Connection
                    </Link>
                  </div>
                ) : (
                  connections.map((db) => (
                    <button
                      key={db.id}
                      onClick={() => setSelectedDatabase(db.id)}
                      style={{
                        padding: '12px',
                        border: selectedDatabase === db.id ? '2px solid var(--primary-purple)' : '1px solid var(--neutral-light-gray)',
                        borderRadius: 'var(--border-radius-lg)',
                        background: selectedDatabase === db.id ? 'var(--primary-purple)' : 'transparent',
                        color: selectedDatabase === db.id ? 'white' : 'var(--neutral-dark)',
                        textAlign: 'left',
                        cursor: 'pointer',
                        transition: 'all var(--transition-normal) var(--easing-default)'
                      }}
                    >
                      <div style={{ display: 'flex', alignItems: 'center', gap: '8px', marginBottom: '4px' }}>
                        <span style={{ fontSize: '16px' }}>{getDatabaseIcon(db.type)}</span>
                        <span style={{ fontWeight: 'var(--font-weight-medium)' }}>{db.name}</span>
                      </div>
                      <div style={{ fontSize: 'var(--font-size-sm)', opacity: 0.8 }}>
                        {db.host}:{db.port} • {db.type}
                      </div>
                    </button>
                  ))
                )}
              </div>
            </div>

            {/* Try Asking */}
            <div className="card">
              <h3 style={{ 
                fontSize: 'var(--font-size-lg)', 
                fontWeight: 'var(--font-weight-semibold)',
                marginBottom: '16px'
              }}>
                Try Asking
              </h3>
              <div style={{ display: 'flex', flexDirection: 'column', gap: '8px' }}>
                <div style={{ 
                  padding: '8px 12px',
                  background: 'var(--neutral-light-gray)',
                  borderRadius: 'var(--border-radius-md)',
                  fontSize: 'var(--font-size-sm)'
                }}>
                  "What is the database name of the connected database?"
                </div>
                <div style={{ 
                  padding: '8px 12px',
                  background: 'var(--neutral-light-gray)',
                  borderRadius: 'var(--border-radius-md)',
                  fontSize: 'var(--font-size-sm)'
                }}>
                  "List down all the tables in the pizza_shop schema"
                </div>
                <div style={{ 
                  padding: '8px 12px',
                  background: 'var(--neutral-light-gray)',
                  borderRadius: 'var(--border-radius-md)',
                  fontSize: 'var(--font-size-sm)'
                }}>
                  "Get me all the payments of more that 20 from the payment table"
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
};

export default ExplorePage; 