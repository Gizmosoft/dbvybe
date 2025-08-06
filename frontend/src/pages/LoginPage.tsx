import { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { Database, Eye, EyeOff, Mail, Lock } from 'lucide-react';

const LoginPage = () => {
  const [showPassword, setShowPassword] = useState(false);
  const [formData, setFormData] = useState({
    email: '',
    password: ''
  });
  const navigate = useNavigate();

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    // Simulate login - in real app this would call an API
    console.log('Login attempt:', formData);
    navigate('/dashboard');
  };

  const handleChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    setFormData({
      ...formData,
      [e.target.name]: e.target.value
    });
  };

  return (
    <div className="min-h-screen" style={{ 
      background: 'linear-gradient(135deg, #667EEA 0%, #764BA2 100%)',
      display: 'flex',
      alignItems: 'center',
      justifyContent: 'center',
      padding: '20px',
      minHeight: '100vh'
    }}>
      <div className="card" style={{ 
        maxWidth: '400px', 
        width: '100%',
        padding: '48px 32px'
      }}>
        <div style={{ textAlign: 'center', marginBottom: '32px' }}>
          <Link to="/" style={{ 
            display: 'inline-flex', 
            alignItems: 'center', 
            gap: '8px',
            textDecoration: 'none',
            color: 'var(--primary-purple)',
            fontWeight: 'var(--font-weight-bold)',
            fontSize: 'var(--font-size-xl)',
            marginBottom: '8px'
          }}>
            <Database size={32} />
            dbVybe
          </Link>
          <h1 style={{ 
            fontSize: 'var(--font-size-2xl)', 
            fontWeight: 'var(--font-weight-bold)',
            marginBottom: '8px'
          }}>
            Welcome back
          </h1>
          <p style={{ color: 'var(--neutral-gray)' }}>
            Sign in to your account to continue
          </p>
        </div>

        <form onSubmit={handleSubmit}>
          <div className="form-group">
            <label htmlFor="email" className="form-label">
              Email address
            </label>
            <div style={{ position: 'relative' }}>
              <Mail size={20} style={{ 
                position: 'absolute', 
                left: '12px', 
                top: '50%', 
                transform: 'translateY(-50%)',
                color: 'var(--neutral-gray)'
              }} />
              <input
                type="email"
                id="email"
                name="email"
                value={formData.email}
                onChange={handleChange}
                className="form-input"
                style={{ paddingLeft: '44px' }}
                placeholder="Enter your email"
                required
              />
            </div>
          </div>

          <div className="form-group">
            <label htmlFor="password" className="form-label">
              Password
            </label>
            <div style={{ position: 'relative' }}>
              <Lock size={20} style={{ 
                position: 'absolute', 
                left: '12px', 
                top: '50%', 
                transform: 'translateY(-50%)',
                color: 'var(--neutral-gray)'
              }} />
              <input
                type={showPassword ? 'text' : 'password'}
                id="password"
                name="password"
                value={formData.password}
                onChange={handleChange}
                className="form-input"
                style={{ paddingLeft: '44px', paddingRight: '44px' }}
                placeholder="Enter your password"
                required
              />
              <button
                type="button"
                onClick={() => setShowPassword(!showPassword)}
                style={{
                  position: 'absolute',
                  right: '12px',
                  top: '50%',
                  transform: 'translateY(-50%)',
                  background: 'none',
                  border: 'none',
                  cursor: 'pointer',
                  color: 'var(--neutral-gray)'
                }}
              >
                {showPassword ? <EyeOff size={20} /> : <Eye size={20} />}
              </button>
            </div>
          </div>

          <div style={{ 
            display: 'flex', 
            justifyContent: 'space-between', 
            alignItems: 'center',
            marginBottom: '24px'
          }}>
            <label style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
              <input type="checkbox" style={{ width: '16px', height: '16px' }} />
              <span style={{ fontSize: 'var(--font-size-sm)' }}>Remember me</span>
            </label>
            <Link to="/forgot-password" style={{ 
              fontSize: 'var(--font-size-sm)',
              color: 'var(--primary-purple)',
              textDecoration: 'none'
            }}>
              Forgot password?
            </Link>
          </div>

          <button type="submit" className="btn btn-primary" style={{ width: '100%' }}>
            Sign in
          </button>
        </form>

        <div style={{ 
          textAlign: 'center', 
          marginTop: '32px',
          paddingTop: '24px',
          borderTop: '1px solid var(--neutral-light-gray)'
        }}>
          <p style={{ color: 'var(--neutral-gray)', marginBottom: '16px' }}>
            Don't have an account?{' '}
            <Link to="/signup" style={{ 
              color: 'var(--primary-purple)',
              textDecoration: 'none',
              fontWeight: 'var(--font-weight-medium)'
            }}>
              Sign up
            </Link>
          </p>
        </div>

        {/* Demo credentials */}
        <div style={{ 
          marginTop: '24px',
          padding: '16px',
          background: 'var(--neutral-light-gray)',
          borderRadius: 'var(--border-radius-lg)',
          fontSize: 'var(--font-size-sm)'
        }}>
          <p style={{ marginBottom: '8px', fontWeight: 'var(--font-weight-medium)' }}>
            Demo credentials:
          </p>
          <p style={{ marginBottom: '4px' }}>
            <strong>Email:</strong> demo@dbvybe.com
          </p>
          <p>
            <strong>Password:</strong> demo123
          </p>
        </div>
      </div>
    </div>
  );
};

export default LoginPage; 