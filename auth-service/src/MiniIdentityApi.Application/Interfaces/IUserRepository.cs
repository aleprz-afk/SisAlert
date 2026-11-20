using MiniIdentityApi.Domain.Entities;

namespace MiniIdentityApi.Application.Interfaces;

public interface IUserRepository
{
    User? FindById(Guid id);
    User? FindByUsernameOrEmail(string value);
    List<User> GetAll();
    void Save(User user);
    void Delete(User user);
}
