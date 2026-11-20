using MiniIdentityApi.Application.Interfaces;
using MiniIdentityApi.Domain.Entities;

namespace MiniIdentityApi.Infrastructure.Repositories;

/// <summary>
/// Repositorio en memoria. Se conserva para las pruebas unitarias y para poder
/// levantar la API sin base de datos si algún día se necesita.
/// </summary>
public class InMemoryUserRepository : IUserRepository
{
    private readonly List<User> _users = new();

    public User? FindById(Guid id) => _users.FirstOrDefault(u => u.Id == id);

    public User? FindByUsernameOrEmail(string value)
    {
        return _users.FirstOrDefault(u =>
            u.Username.Equals(value, StringComparison.OrdinalIgnoreCase) ||
            u.Email.Equals(value, StringComparison.OrdinalIgnoreCase));
    }

    public List<User> GetAll() => _users;

    public void Save(User user)
    {
        var index = _users.FindIndex(u => u.Id == user.Id);
        if (index >= 0)
            _users[index] = user;
        else
            _users.Add(user);
    }

    public void Delete(User user) => _users.RemoveAll(u => u.Id == user.Id);
}
