from keras.layers import Input, Dense
from keras.models import Model, model_from_json
import numpy as np


class PolicyValueNetwork:

    def __init__(self, optimizer="adam"):

        """
        Policy-value network for Monte Carlo Tree Search
        """
        
        self.optimizer = optimizer
        self.loss = {"action": "kullback_leibler_divergence",
                     "value": "mean_squared_error"}
        self.loss_weights = {"action": 1, "value": 1}
        self.model.compile(optimizer=self.optimizer,
                           loss=self.loss,
                           loss_weights=self.loss_weights)
        
    def fit(self, *args, **kwargs):
        self.model.fit(*args, **kwargs)

    def save_weights(self, filename):
        self.model.save_weights(filaname)
        
    def evaluate(self, input):
        """
        Evaluate the policy value network with input

        Parameters
        ----------
        input : array
            input given to the input layer
        
        Return
        ------
        policy_output[0] : array
            output from the policy head
        value_output[0] : array
            output from the value head
        """
        policy_output, value_output = self.model.predict(np.array([input]))
        return policy_output[0], value_output[0]

    def deepcopy(self):
        raise NotImplementedError()

class PolicyValueMLP(PolicyValueNetwork):

    def __init__(self, input_dim, hidden_dim, action_dim, value_dim=1,
                 activation="relu", optimizer="adam"):
        """
        Multi layer perceptron with the following structure:

           input
             |
           hidden
             |
          --------
          |      |
        action value

        Parameters
        ----------
        input_dim : int
            number of input units
        hidden_dim : int
            number of hidden units
        action_dim : int
            number of action units
        value_dim : int, optional (default=1)
            number of value units
        activation : string, optional (default="relu")
            activation function of the hidden layer
        optimizer : string, optional (default="adam")
            optimizer to train the network
        """
        self.input_dim = input_dim
        self.hidden_dim = hidden_dim
        self.action_dim = action_dim
        self.value_dim = value_dim
        self.activation = activation
        self.optimizer = optimizer
        input_layer = Input(shape=(input_dim,), name="input")
        hidden_layer = Dense(hidden_dim, activation=activation, name="hidden")(input_layer)
        action_layer = Dense(action_dim, activation="softmax", name="action")(hidden_layer)
        value_layer = Dense(value_dim, activation="linear", name="value")(hidden_layer)
        
        self.model = Model(inputs=input_layer,
                           outputs=[action_layer, value_layer])
        super().__init__(optimizer)

    def deepcopy(self):
        """
        json_string = self.model.to_json()
        model = model_from_json(json_string)
        model.set_weights(self.model.get_weights())
        """
        copy = PolicyValueMLP(input_dim=self.input_dim,
                              hidden_dim=self.hidden_dim,
                              action_dim=self.action_dim,
                              value_dim=self.value_dim,
                              activation=self.activation,
                              optimizer=self.optimizer)
        config = self.model.get_config()
        copy.model = Model.from_config(config)
        return copy

    def load_weights(self, filename):
        self.model.load_weights(filename)

if __name__ == "__main__":

    import numpy as np
    from keras.callbacks import CSVLogger
    import os
    import argparse

    parser = argparse.ArgumentParser(description="test run")
    parser.add_argument("optimizer", help="optimizer")
    parser.add_argument("activation", help="activation")
    parser.add_argument("n_hidden", help="number of hiddens")
    args = parser.parse_args()    
    
    def make_data(N, input_dim):
        states = [np.random.normal(0, 1, input_dim) for _ in range(N)]
        actions = [np.abs(state)/np.sum(np.abs(state)) for state in states]
        values = [np.prod(state) for state in states]
        states = np.vstack(states)
        actions = np.vstack(actions)
        values = np.vstack(values)
        return states, [actions, values]
    
    input_dim = 2
    hidden_dim = int(args.n_hidden)
    action_dim = input_dim

    Xtrain, Ytrain = make_data(10000, input_dim)
    Xtest, Ytest = make_data(10000, input_dim)

    logdir = "log_mlp"
    if not os.path.exists(logdir):
        os.mkdir(logdir)
    logfile = "log_" + args.optimizer + "_" + args.activation + str(hidden_dim) + ".csv"
    logfile = os.path.join(logdir, logfile)
    callbacks = [CSVLogger(logfile)]

    model = PolicyValueMLP(input_dim, hidden_dim, action_dim,
                           activation=args.activation, optimizer=args.optimizer)
    model.fit(Xtrain, Ytrain, epochs=100, callbacks=callbacks)

    Ypred = model.predict(Xtest)

    actions_test, values_test = Ytest
    actions_pred, values_pred = Ypred

    print(np.mean((actions_pred - actions_test)**2),
          np.mean((values_pred - values_test)**2))
